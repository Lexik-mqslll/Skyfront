package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.StreamCodec;
import dev.krutlexik.skyfront.port.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;

import java.util.function.Supplier;

public class ShahedExplosionPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShahedExplosionPacket> TYPE = new CustomPacketPayload.Type<>(new ResourceLocation(SkyfrontMod.MOD_ID, "ShahedExplosionPacket".toLowerCase()));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShahedExplosionPacket> STREAM_CODEC = StreamCodec.ofMember(ShahedExplosionPacket::toBytes, ShahedExplosionPacket::new);
    @Override
    public CustomPacketPayload.Type<ShahedExplosionPacket> type() { return TYPE; }
    private final Vec3 position;
    private final float particleMultiplier;
    private final boolean customSounds;

    public ShahedExplosionPacket(Vec3 position, float particleMultiplier, boolean customSounds) {
        this.position = position;
        this.particleMultiplier = particleMultiplier;
        this.customSounds = customSounds;
    }

    public ShahedExplosionPacket(RegistryFriendlyByteBuf buf) {
        this.position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.particleMultiplier = buf.readFloat();
        this.customSounds = buf.readBoolean();
    }

    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(this.position.x);
        buf.writeDouble(this.position.y);
        buf.writeDouble(this.position.z);
        buf.writeFloat(this.particleMultiplier);
        buf.writeBoolean(this.customSounds);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleExplosion(this.position, this.particleMultiplier,
                this.customSounds));
    }

    public Vec3 getPosition() {
        return this.position;
    }

    public float getParticleMultiplier() {
        return this.particleMultiplier;
    }
}
