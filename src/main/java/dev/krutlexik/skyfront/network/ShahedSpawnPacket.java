package dev.krutlexik.skyfront.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.function.Supplier;

public class ShahedSpawnPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShahedSpawnPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "ShahedSpawnPacket".toLowerCase()));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShahedSpawnPacket> STREAM_CODEC = StreamCodec.ofMember(ShahedSpawnPacket::toBytes, ShahedSpawnPacket::new);
    @Override
    public CustomPacketPayload.Type<ShahedSpawnPacket> type() { return TYPE; }
    private final int entityId;
    private final Vec3 startPos;
    private final BlockPos targetPos;
    private final String diveMode;
    private final float healthFract;
    private final boolean falling;
    private final float diveProgress;
    /** False while cruising between route waypoints — kept in sync for drones beyond tracking range. */
    private final boolean finalLeg;

    public ShahedSpawnPacket(int entityId, Vec3 startPos, BlockPos targetPos, String diveMode,
                             float healthFract, boolean falling, float diveProgress, boolean finalLeg) {
        this.entityId = entityId;
        this.startPos = startPos;
        this.targetPos = targetPos;
        this.diveMode = diveMode;
        this.healthFract = healthFract;
        this.falling = falling;
        this.diveProgress = diveProgress;
        this.finalLeg = finalLeg;
    }

    public ShahedSpawnPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.startPos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.targetPos = buf.readBlockPos();
        this.diveMode = buf.readUtf();
        this.healthFract = buf.readFloat();
        this.falling = buf.readBoolean();
        this.diveProgress = buf.readFloat();
        this.finalLeg = buf.readBoolean();
    }

    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeDouble(this.startPos.x);
        buf.writeDouble(this.startPos.y);
        buf.writeDouble(this.startPos.z);
        buf.writeBlockPos(this.targetPos);
        buf.writeUtf(this.diveMode);
        buf.writeFloat(this.healthFract);
        buf.writeBoolean(this.falling);
        buf.writeFloat(this.diveProgress);
        buf.writeBoolean(this.finalLeg);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleShahedSpawn(this.entityId, this.startPos, this.targetPos,
                this.diveMode, this.healthFract, this.falling, this.diveProgress, this.finalLeg));
    }

    public float getHealthFract() {
        return this.healthFract;
    }

    public boolean isFalling() {
        return this.falling;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public Vec3 getStartPos() {
        return this.startPos;
    }

    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    public String getDiveMode() {
        return this.diveMode;
    }
}
