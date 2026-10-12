package dev.krutlexik.skyfront.network;

import net.minecraft.client.Minecraft;
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

public class OpenRadarScreenPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenRadarScreenPacket> TYPE = new CustomPacketPayload.Type<>(new ResourceLocation(SkyfrontMod.MOD_ID, "OpenRadarScreenPacket".toLowerCase()));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRadarScreenPacket> STREAM_CODEC = StreamCodec.ofMember(OpenRadarScreenPacket::toBytes, OpenRadarScreenPacket::new);
    @Override
    public CustomPacketPayload.Type<OpenRadarScreenPacket> type() { return TYPE; }
    private final BlockPos pos;

    public OpenRadarScreenPacket(BlockPos pos) {
        this.pos = pos;
    }

    public OpenRadarScreenPacket(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.krutlexik.skyfront.client.gui.ShahedRadarScreen.open(this.pos));
    }
}
