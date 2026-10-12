package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.client.sound.SirenSoundInstance;
import net.minecraft.core.BlockPos;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;

import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.StreamCodec;
import dev.krutlexik.skyfront.port.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;

import java.util.function.Supplier;

public class SirenSoundPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SirenSoundPacket> TYPE = new CustomPacketPayload.Type<>(new ResourceLocation(SkyfrontMod.MOD_ID, "SirenSoundPacket".toLowerCase()));
    public static final StreamCodec<RegistryFriendlyByteBuf, SirenSoundPacket> STREAM_CODEC = StreamCodec.ofMember(SirenSoundPacket::toBytes, SirenSoundPacket::new);
    @Override
    public CustomPacketPayload.Type<SirenSoundPacket> type() { return TYPE; }
    private final BlockPos pos;
    private final boolean stop;
    /** Audible range in blocks, from the server config (a connected client can't read it itself). */
    private final int range;

    public SirenSoundPacket(BlockPos pos) {
        this(pos, false, dev.krutlexik.skyfront.config.SkyfrontConfig.SIREN_RANGE.get());
    }

    public SirenSoundPacket(BlockPos pos, boolean stop) {
        this(pos, stop, dev.krutlexik.skyfront.config.SkyfrontConfig.SIREN_RANGE.get());
    }

    public SirenSoundPacket(BlockPos pos, boolean stop, int range) {
        this.pos = pos;
        this.stop = stop;
        this.range = range;
    }

    public SirenSoundPacket(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.stop = buf.readBoolean();
        this.range = buf.readVarInt();
    }

    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.stop);
        buf.writeVarInt(this.range);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> this.handleClient());
    }

    private void handleClient() {
        if (this.stop) {
            SirenSoundInstance.stopSiren(this.pos);
        } else {
            SirenSoundInstance.playSiren(this.pos, this.range);
        }
    }
}
