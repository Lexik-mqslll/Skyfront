package dev.krutlexik.skyfront.client;

/** Client cache of the radar terrain map: a square grid of chunk colors (0 = unknown) around a radar. */
public class ClientWorldMapData {
    private static final ClientWorldMapData INSTANCE = new ClientWorldMapData();

    private int centerChunkX;
    private int centerChunkZ;
    private int radiusChunks;
    private int[] colors;
    private int version;
    private boolean scanComplete;
    private int scanProgress;
    private int totalChunks;

    private ClientWorldMapData() {
    }

    public static ClientWorldMapData getInstance() {
        return INSTANCE;
    }

    public void update(int centerChunkX, int centerChunkZ, int radiusChunks, int[] colors,
                       boolean scanComplete, int scanProgress, int totalChunks) {
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.radiusChunks = radiusChunks;
        this.colors = colors;
        this.scanComplete = scanComplete;
        this.scanProgress = scanProgress;
        this.totalChunks = totalChunks;
        this.version++;
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

    public int[] getColors() {
        return colors;
    }

    public int getVersion() {
        return version;
    }

    public boolean hasData() {
        return colors != null && colors.length > 0;
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

    public float getScanPercent() {
        if (totalChunks == 0) return 100.0f;
        return (float) scanProgress / totalChunks * 100.0f;
    }

    public void clear() {
        colors = null;
        version++;
    }
}
