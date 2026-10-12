package dev.krutlexik.skyfront.worldmap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the radar terrain map at server start, fully off the main thread: region files are enumerated on a
 * worker thread, raw chunk NBT is read through the async IO worker ({@code ChunkMap#read}), and
 * heightmap/top-block parsing happens on the scanner thread — no chunk is ever LOADED into the world and the
 * main thread does zero work.
 */
public class WorldMapScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("SFW-WorldMap");

    private static WorldMapScanner instance;
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SFW-MapScanner");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private final AtomicInteger progress = new AtomicInteger(0);
    private volatile int totalChunks = 0;

    private WorldMapScanner() {
    }

    public static WorldMapScanner getInstance() {
        if (instance == null) {
            instance = new WorldMapScanner();
        }
        return instance;
    }

    public void startScan(ServerLevel level) {
        if (!isScanning.compareAndSet(false, true)) {
            LOGGER.info("[SFW] Map scan already in progress");
            return;
        }

        WorldMapData mapData = WorldMapData.get(level);
        int minY = level.getMinBuildHeight();
        int heightBits = Mth.ceillog2(level.getHeight() + 1);

        CompletableFuture.runAsync(() -> {
            try {
                List<ChunkPos> existing = findExistingChunks(level);
                List<ChunkPos> toScan = new ArrayList<>();
                for (ChunkPos pos : existing) {
                    if (!mapData.isChunkScanned(pos.x, pos.z)) {
                        toScan.add(pos);
                    }
                }
                totalChunks = toScan.size();
                progress.set(0);
                LOGGER.info("[SFW] World map: {} chunks on disk, {} new to scan ({} colors cached)",
                        existing.size(), toScan.size(), mapData.getColorCount());

                int batchSize = 128;
                for (int i = 0; i < toScan.size(); i += batchSize) {
                    if (!isScanning.get()) {
                        LOGGER.info("[SFW] Map scan cancelled");
                        return;
                    }
                    int end = Math.min(i + batchSize, toScan.size());
                    List<ChunkPos> batch = toScan.subList(i, end);

                    List<CompletableFuture<Optional<CompoundTag>>> reads = new ArrayList<>(batch.size());
                    for (ChunkPos pos : batch) {
                        reads.add(level.getChunkSource().chunkMap.read(pos));
                    }
                    for (int k = 0; k < batch.size(); k++) {
                        ChunkPos pos = batch.get(k);
                        int color = 0;
                        try {
                            Optional<CompoundTag> tag = reads.get(k).get(30, TimeUnit.SECONDS);
                            if (tag.isPresent()) {
                                color = parseChunkColor(tag.get(), minY, heightBits);
                            }
                        } catch (Exception ignored) {
                        }
                        mapData.putChunkColor(pos.x, pos.z, color);
                    }

                    int done = progress.addAndGet(batch.size());
                    if (done % 8192 < batchSize) {
                        LOGGER.info("[SFW] Map scan progress: {}/{} chunks ({}%)",
                                done, totalChunks, totalChunks == 0 ? 100 : done * 100 / totalChunks);
                    }
                }

                mapData.setScanComplete(true);
                LOGGER.info("[SFW] Map scan complete! {} chunk colors in the map", mapData.getColorCount());
            } catch (Exception e) {
                LOGGER.error("[SFW] Error during map scan", e);
            } finally {
                isScanning.set(false);
            }
        }, scanExecutor);
    }

    private List<ChunkPos> findExistingChunks(ServerLevel level) {
        List<ChunkPos> chunks = new ArrayList<>();
        Path worldPath = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        File regionDir = worldPath.resolve("region").toFile();
        if (!regionDir.exists() || !regionDir.isDirectory()) {
            LOGGER.warn("[SFW] Region directory not found: {}", regionDir);
            return chunks;
        }
        File[] regionFiles = regionDir.listFiles((dir, name) -> name.endsWith(".mca"));
        if (regionFiles == null) {
            return chunks;
        }
        for (File regionFile : regionFiles) {
            String name = regionFile.getName();
            String[] parts = name.replace("r.", "").replace(".mca", "").split("\\.");
            if (parts.length != 2) {
                continue;
            }
            try {
                int regionX = Integer.parseInt(parts[0]);
                int regionZ = Integer.parseInt(parts[1]);
                chunks.addAll(getChunksInRegion(regionFile, regionX, regionZ));
            } catch (NumberFormatException e) {
                LOGGER.warn("[SFW] Invalid region file name: {}", name);
            }
        }
        return chunks;
    }

    private List<ChunkPos> getChunksInRegion(File regionFile, int regionX, int regionZ) {
        List<ChunkPos> chunks = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(regionFile, "r")) {
            for (int localZ = 0; localZ < 32; localZ++) {
                for (int localX = 0; localX < 32; localX++) {
                    raf.seek(4L * (localX + localZ * 32));
                    if (raf.readInt() != 0) {
                        chunks.add(new ChunkPos(regionX * 32 + localX, regionZ * 32 + localZ));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[SFW] Error reading region file: {}", regionFile.getName());
        }
        return chunks;
    }

    /** Samples the chunk's center column (8,8): surface height + top block -> shaded map color. */
    private int parseChunkColor(CompoundTag tag, int minY, int heightBits) {
        if (!"minecraft:full".equals(tag.getString("Status"))) {
            return 0;
        }
        long[] heightmap = tag.getCompound("Heightmaps").getLongArray("WORLD_SURFACE");
        if (heightmap.length == 0) {
            return 0;
        }
        int stored = readPacked(heightmap, heightBits, 8 * 16 + 8);
        int topY = minY + stored - 1;
        if (stored <= 0) {
            return 0;
        }
        String block = findBlockAt(tag, 8, topY, 8);
        return colorFor(block, topY);
    }

    /** Reads one entry from vanilla's strict bit packing (no entry crosses a long boundary). */
    private static int readPacked(long[] data, int bits, int index) {
        int valuesPerLong = 64 / bits;
        int word = index / valuesPerLong;
        if (word >= data.length) {
            return 0;
        }
        int shift = (index % valuesPerLong) * bits;
        return (int) ((data[word] >>> shift) & ((1L << bits) - 1));
    }

    private static String findBlockAt(CompoundTag chunkTag, int x, int y, int z) {
        ListTag sections = chunkTag.getList("sections", Tag.TAG_COMPOUND);
        int sectionY = Math.floorDiv(y, 16);
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.getCompound(i);
            if (section.getByte("Y") != sectionY) {
                continue;
            }
            CompoundTag blockStates = section.getCompound("block_states");
            ListTag palette = blockStates.getList("palette", Tag.TAG_COMPOUND);
            if (palette.isEmpty()) {
                return "minecraft:air";
            }
            if (palette.size() == 1 || !blockStates.contains("data")) {
                return palette.getCompound(0).getString("Name");
            }
            long[] data = blockStates.getLongArray("data");
            int bits = Math.max(4, Mth.ceillog2(palette.size()));
            int index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
            int paletteIndex = readPacked(data, bits, index);
            if (paletteIndex >= palette.size()) {
                return "minecraft:air";
            }
            return palette.getCompound(paletteIndex).getString("Name");
        }
        return "minecraft:air";
    }

    /** Curated low-detail palette + quantized height shading (keeps unique colors under 256). */
    private static int colorFor(String blockId, int y) {
        String id = blockId.startsWith("minecraft:") ? blockId.substring(10) : blockId;
        int base = baseColor(id);
        if (base == 0) {
            return 0;
        }
        boolean water = id.contains("water") || id.endsWith("ice");
        float shade;
        if (water) {
            shade = 1.15f;
        } else {
            float t = Mth.clamp((y - 40) / 140.0f, 0.0f, 1.0f);
            shade = 1.0f + 0.3f * (Math.round(t * 7.0f) / 7.0f);
        }
        int r = Math.min(255, (int) (((base >> 16) & 0xFF) * shade));
        int g = Math.min(255, (int) (((base >> 8) & 0xFF) * shade));
        int b = Math.min(255, (int) ((base & 0xFF) * shade));
        int color = (r << 16) | (g << 8) | b;
        return color == 0 ? 0x010101 : color;
    }

    private static int baseColor(String id) {
        if (id.equals("air") || id.equals("cave_air") || id.equals("void_air")) return 0;
        if (id.contains("water")) return 0x5C93E0;
        if (id.endsWith("ice")) return 0xB8D8F5;
        if (id.equals("lava")) return 0xFF8A2E;
        if (id.equals("snow") || id.equals("snow_block") || id.equals("powder_snow")) return 0xFAFAFF;
        if (id.equals("grass_block")) return 0x9AD16A;
        if (id.endsWith("_leaves") || id.equals("mangrove_roots")) return 0x6FAE49;
        if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_planks")) return 0xA8814F;
        if (id.contains("sandstone")) return 0xEADCA2;
        if (id.equals("sand")) return 0xF2E9B4;
        if (id.equals("red_sand")) return 0xDD8447;
        if (id.equals("gravel")) return 0xAFAFAF;
        if (id.equals("clay")) return 0xC2CBD9;
        if (id.contains("deepslate")) return 0x8C8C8C;
        if (id.equals("dirt") || id.equals("coarse_dirt") || id.equals("rooted_dirt") || id.equals("dirt_path") || id.equals("farmland")) return 0xB68A5E;
        if (id.equals("podzol") || id.equals("mud") || id.equals("muddy_mangrove_roots")) return 0x8A6E55;
        if (id.equals("mycelium")) return 0xA894A8;
        if (id.contains("moss")) return 0x8ABF63;
        if (id.contains("terracotta")) return 0xDB8B63;
        if (id.contains("basalt") || id.equals("blackstone")) return 0x76767E;
        if (id.equals("obsidian") || id.equals("crying_obsidian")) return 0x4A4A66;
        if (id.contains("netherrack") || id.contains("nether")) return 0xB44A4A;
        if (id.contains("end_stone")) return 0xF0F0B4;
        if (id.contains("stone") || id.contains("andesite") || id.contains("diorite") || id.contains("granite")
                || id.contains("tuff") || id.contains("calcite") || id.contains("cobble")) return 0xB4B4B4;
        if (id.contains("grass") || id.contains("fern") || id.contains("bush")) return 0x9AD16A;
        if (id.contains("flower") || id.contains("tulip") || id.contains("orchid")) return 0xB0CE73;
        if (id.contains("mushroom") || id.contains("fungus")) return 0xC49E8A;
        if (id.contains("bamboo")) return 0x8ABF4E;
        if (id.contains("cactus")) return 0x77B052;
        if (id.contains("pumpkin") || id.contains("melon")) return 0xC4B04E;
        if (id.contains("coral")) return 0xE68ACE;
        if (id.contains("kelp") || id.contains("seagrass") || id.contains("lily")) return 0x529E77;
        return 0xA0A0A0;
    }

    public void stopScan() {
        isScanning.set(false);
    }

    public boolean isScanning() {
        return isScanning.get();
    }

    public int getProgress() {
        return progress.get();
    }

    public int getTotalChunks() {
        return totalChunks;
    }
}
