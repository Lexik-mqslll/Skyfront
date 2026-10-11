package dev.krutlexik.skyfront.worldmap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server-side terrain map: one packed 0xRRGGBB color per chunk (16-block resolution — exactly the radar
 * display's pixel density), built once by {@link WorldMapScanner} from the region files and persisted with
 * the world. 0 means "not scanned / no data".
 */
public class WorldMapData extends SavedData {
    private static final String DATA_NAME = "skyfront_world_map";

    /**
     * Bump whenever the scanner's color palette or shading changes: stored maps are keyed to it, and a
     * mismatch discards the cache so the world is re-scanned with the new colors (otherwise already-scanned
     * chunks are skipped forever and keep their old look).
     */
    private static final int PALETTE_VERSION = 2;

    private final Map<Long, Integer> chunkColors = new ConcurrentHashMap<>();
    private final Set<Long> scannedChunks = ConcurrentHashMap.newKeySet();
    private volatile boolean scanComplete = false;

    public WorldMapData() {
    }

    public static WorldMapData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        WorldMapData data = new WorldMapData();
        if (tag.getInt("paletteVersion") != PALETTE_VERSION) {
            return data;
        }
        long[] keys = tag.getLongArray("colorKeys");
        int[] values = tag.getIntArray("colorValues");
        for (int i = 0; i < keys.length && i < values.length; i++) {
            data.chunkColors.put(keys[i], values[i]);
        }
        for (long scanned : tag.getLongArray("scanned")) {
            data.scannedChunks.add(scanned);
        }
        data.scanComplete = tag.getBoolean("scanComplete");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        long[] keys = new long[chunkColors.size()];
        int[] values = new int[chunkColors.size()];
        int i = 0;
        for (Map.Entry<Long, Integer> entry : chunkColors.entrySet()) {
            if (i >= keys.length) {
                break;
            }
            keys[i] = entry.getKey();
            values[i] = entry.getValue();
            i++;
        }
        tag.putLongArray("colorKeys", keys);
        tag.putIntArray("colorValues", values);
        long[] scanned = new long[scannedChunks.size()];
        int j = 0;
        for (Long chunk : scannedChunks) {
            if (j >= scanned.length) {
                break;
            }
            scanned[j++] = chunk;
        }
        tag.putLongArray("scanned", scanned);
        tag.putBoolean("scanComplete", scanComplete);
        tag.putInt("paletteVersion", PALETTE_VERSION);
        return tag;
    }

    public static WorldMapData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        SavedData.Factory<WorldMapData> factory = new SavedData.Factory<>(WorldMapData::new, WorldMapData::load, null);
        return storage.computeIfAbsent(factory, DATA_NAME);
    }

    public void putChunkColor(int chunkX, int chunkZ, int color) {
        if (color != 0) {
            chunkColors.put(packChunkPos(chunkX, chunkZ), color);
        }
        scannedChunks.add(packChunkPos(chunkX, chunkZ));
        setDirty();
    }

    public boolean isChunkScanned(int chunkX, int chunkZ) {
        return scannedChunks.contains(packChunkPos(chunkX, chunkZ));
    }

    /**
     * Collects a square (2r+1)x(2r+1) grid of chunk colors around the center, row-major (z rows, x columns).
     * 0 = unknown.
     */
    public int[] collectSquare(int centerChunkX, int centerChunkZ, int radiusChunks) {
        int size = radiusChunks * 2 + 1;
        int[] grid = new int[size * size];
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                Integer color = chunkColors.get(packChunkPos(centerChunkX + dx, centerChunkZ + dz));
                if (color != null) {
                    grid[(dz + radiusChunks) * size + (dx + radiusChunks)] = color;
                }
            }
        }
        return grid;
    }

    public boolean isScanComplete() {
        return scanComplete;
    }

    public void setScanComplete(boolean complete) {
        this.scanComplete = complete;
        setDirty();
    }

    public int getColorCount() {
        return chunkColors.size();
    }

    /** Wipes the map for a full rescan. */
    public void clear() {
        chunkColors.clear();
        scannedChunks.clear();
        scanComplete = false;
        setDirty();
    }

    public static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }
}
