package dev.krutlexik.skyfront.network;

import net.minecraft.core.BlockPos;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;

import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.StreamCodec;
import dev.krutlexik.skyfront.port.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RadarUpdatePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RadarUpdatePacket> TYPE = new CustomPacketPayload.Type<>(new ResourceLocation(SkyfrontMod.MOD_ID, "RadarUpdatePacket".toLowerCase()));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarUpdatePacket> STREAM_CODEC = StreamCodec.ofMember(RadarUpdatePacket::toBytes, RadarUpdatePacket::new);
    @Override
    public CustomPacketPayload.Type<RadarUpdatePacket> type() { return TYPE; }
    private final BlockPos radarPos;
    /** Server-configured detection radius, so the client draws the ring at the true range. */
    private final int detectRange;
    private final List<Vec3> dronePositions;

    public RadarUpdatePacket(BlockPos radarPos, int detectRange, List<Vec3> dronePositions) {
        this.radarPos = radarPos;
        this.detectRange = detectRange;
        this.dronePositions = dronePositions;
    }

    public RadarUpdatePacket(RegistryFriendlyByteBuf buf) {
        this.radarPos = buf.readBlockPos();
        this.detectRange = buf.readInt();
        int count = buf.readInt();
        this.dronePositions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            this.dronePositions.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
    }

    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.radarPos);
        buf.writeInt(this.detectRange);
        buf.writeInt(this.dronePositions.size());
        for (Vec3 pos : this.dronePositions) {
            buf.writeDouble(pos.x);
            buf.writeDouble(pos.y);
            buf.writeDouble(pos.z);
        }
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.krutlexik.skyfront.client.gui.ShahedRadarScreen
                .updateDronePositions(radarPos, detectRange, dronePositions));
    }
}
