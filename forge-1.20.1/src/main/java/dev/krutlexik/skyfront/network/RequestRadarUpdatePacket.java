package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.entity.ShahedEntity;
import net.minecraft.core.BlockPos;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.StreamCodec;
import dev.krutlexik.skyfront.port.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RequestRadarUpdatePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestRadarUpdatePacket> TYPE = new CustomPacketPayload.Type<>(new ResourceLocation(SkyfrontMod.MOD_ID, "RequestRadarUpdatePacket".toLowerCase()));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRadarUpdatePacket> STREAM_CODEC = StreamCodec.ofMember(RequestRadarUpdatePacket::toBytes, RequestRadarUpdatePacket::new);
    @Override
    public CustomPacketPayload.Type<RequestRadarUpdatePacket> type() { return TYPE; }
    private final BlockPos radarPos;

    public RequestRadarUpdatePacket(BlockPos radarPos) {
        this.radarPos = radarPos;
    }

    public RequestRadarUpdatePacket(RegistryFriendlyByteBuf buf) {
        this.radarPos = buf.readBlockPos();
    }

    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.radarPos);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) ctx.player();
            if (player == null) return;
            
            ServerLevel level = player.serverLevel();
            Vec3 radarCenter = Vec3.atCenterOf(radarPos);
            int range = dev.krutlexik.skyfront.config.SkyfrontConfig.RADAR_DETECT_RANGE.get();

            AABB searchBox = new AABB(
                radarCenter.x - range, level.getMinBuildHeight(), radarCenter.z - range,
                radarCenter.x + range, level.getMaxBuildHeight(), radarCenter.z + range
            );

            double rangeSq = (double) range * range;
            List<Vec3> dronePositions = new ArrayList<>();
            for (Entity entity : level.getEntities(null, searchBox)) {
                if (entity instanceof ShahedEntity) {
                    double dx = entity.getX() - radarCenter.x;
                    double dz = entity.getZ() - radarCenter.z;
                    if (dx * dx + dz * dz <= rangeSq) {
                        dronePositions.add(entity.position());
                    }
                }
            }

            NetworkHandler.sendToPlayer(player, new RadarUpdatePacket(radarPos, range, dronePositions));
        });
    }
}
