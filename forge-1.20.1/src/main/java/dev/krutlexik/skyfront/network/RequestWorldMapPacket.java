package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.worldmap.WorldMapData;
import dev.krutlexik.skyfront.worldmap.WorldMapScanner;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.StreamCodec;
import dev.krutlexik.skyfront.port.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;

import java.util.function.Supplier;

public class RequestWorldMapPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestWorldMapPacket> TYPE = new CustomPacketPayload.Type<>(new ResourceLocation(SkyfrontMod.MOD_ID, "RequestWorldMapPacket".toLowerCase()));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWorldMapPacket> STREAM_CODEC = StreamCodec.ofMember(RequestWorldMapPacket::toBytes, RequestWorldMapPacket::new);
    @Override
    public CustomPacketPayload.Type<RequestWorldMapPacket> type() { return TYPE; }
    private final int centerX;
    private final int centerZ;
    private final int radius;
    
    public RequestWorldMapPacket(int centerX, int centerZ, int radius) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
    }
    
    public RequestWorldMapPacket(RegistryFriendlyByteBuf buf) {
        this.centerX = buf.readInt();
        this.centerZ = buf.readInt();
        this.radius = buf.readInt();
    }
    
    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeInt(centerX);
        buf.writeInt(centerZ);
        buf.writeInt(radius);
    }
    
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            WorldMapData mapData = WorldMapData.get(level);
            WorldMapScanner scanner = WorldMapScanner.getInstance();

            int radiusChunks = Math.min(Math.max(this.radius, 16), 2048) >> 4;
            int centerChunkX = this.centerX >> 4;
            int centerChunkZ = this.centerZ >> 4;
            int[] grid = mapData.collectSquare(centerChunkX, centerChunkZ, radiusChunks);

            NetworkHandler.sendToPlayer(player, WorldMapPacket.fromGrid(
                    centerChunkX, centerChunkZ, radiusChunks,
                    mapData.isScanComplete(),
                    scanner.getProgress(),
                    scanner.getTotalChunks(),
                    grid));
        });
    }
}
