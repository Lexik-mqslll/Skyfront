package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.IPayloadContext;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;
import dev.krutlexik.skyfront.port.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -&gt; client: a palette-compressed square grid of chunk colors around a radar (one byte per chunk +
 * a palette of up to 256 colors — a 2000-block radius costs ~63 KB instead of 250).
 */
public class WorldMapPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WorldMapPacket> TYPE = new CustomPacketPayload.Type<>(new ResourceLocation(SkyfrontMod.MOD_ID, "worldmappacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldMapPacket> STREAM_CODEC = StreamCodec.ofMember(WorldMapPacket::toBytes, WorldMapPacket::new);

    @Override
    public CustomPacketPayload.Type<WorldMapPacket> type() {
        return TYPE;
    }

    private final int centerChunkX;
    private final int centerChunkZ;
    private final int radiusChunks;
    private final boolean scanComplete;
    private final int scanProgress;
    private final int totalChunks;
    private final int[] palette;
    private final byte[] cells;

    public WorldMapPacket(int centerChunkX, int centerChunkZ, int radiusChunks,
                          boolean scanComplete, int scanProgress, int totalChunks,
                          int[] palette, byte[] cells) {
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radiusChunks = radiusChunks;
        this.scanComplete = scanComplete;
        this.scanProgress = scanProgress;
        this.totalChunks = totalChunks;
        this.palette = palette;
        this.cells = cells;
    }

    private static final int[] QUANTIZE_MASKS = {0xFFFFFF, 0xFEFEFE, 0xFCFCFC, 0xF8F8F8, 0xF0F0F0, 0xE0E0E0, 0xC0C0C0, 0x808080};

    /** Palette-compresses a raw color grid; quantizes colors if they somehow exceed 255. */
    public static WorldMapPacket fromGrid(int centerChunkX, int centerChunkZ, int radiusChunks,
                                          boolean scanComplete, int scanProgress, int totalChunks,
                                          int[] grid) {
        for (int mask : QUANTIZE_MASKS) {
            Map<Integer, Integer> indexOf = new HashMap<>();
            int[] palette = new int[256];
            indexOf.put(0, 0);
            int paletteSize = 1;
            byte[] cells = new byte[grid.length];
            boolean overflow = false;
            for (int i = 0; i < grid.length; i++) {
                int color = grid[i] == 0 ? 0 : Math.max(1, grid[i] & mask);
                Integer index = indexOf.get(color);
                if (index == null) {
                    if (paletteSize >= 256) {
                        overflow = true;
                        break;
                    }
                    index = paletteSize;
                    palette[paletteSize++] = color;
                    indexOf.put(color, index);
                }
                cells[i] = (byte) (int) index;
            }
            if (!overflow) {
                int[] trimmed = new int[paletteSize];
                System.arraycopy(palette, 0, trimmed, 0, paletteSize);
                return new WorldMapPacket(centerChunkX, centerChunkZ, radiusChunks,
                        scanComplete, scanProgress, totalChunks, trimmed, cells);
            }
        }
        return new WorldMapPacket(centerChunkX, centerChunkZ, radiusChunks,
                scanComplete, scanProgress, totalChunks, new int[]{0}, new byte[grid.length]);
    }

    public WorldMapPacket(RegistryFriendlyByteBuf buf) {
        this.centerChunkX = buf.readInt();
        this.centerChunkZ = buf.readInt();
        this.radiusChunks = buf.readInt();
        this.scanComplete = buf.readBoolean();
        this.scanProgress = buf.readInt();
        this.totalChunks = buf.readInt();
        int paletteSize = buf.readShort() & 0xFFFF;
        this.palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            this.palette[i] = buf.readInt();
        }
        this.cells = buf.readByteArray();
    }

    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeInt(centerChunkX);
        buf.writeInt(centerChunkZ);
        buf.writeInt(radiusChunks);
        buf.writeBoolean(scanComplete);
        buf.writeInt(scanProgress);
        buf.writeInt(totalChunks);
        buf.writeShort(palette.length);
        for (int color : palette) {
            buf.writeInt(color);
        }
        buf.writeByteArray(cells);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleWorldMapPacket(this));
    }

    /** Decodes back into a raw color grid. */
    public int[] decodeGrid() {
        int[] grid = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            int index = cells[i] & 0xFF;
            grid[i] = index < palette.length ? palette[index] : 0;
        }
        return grid;
    }

    public int getCenterChunkX() {
        return centerChunkX;
    }

    public int getCenterChunkZ() {
        return centerChunkZ;
    }

    public int getRadiusChunks() {
        return radiusChunks;
    }

    public boolean isScanComplete() {
        return scanComplete;
    }

    public int getScanProgress() {
        return scanProgress;
    }

    public int getTotalChunks() {
        return totalChunks;
    }
}
