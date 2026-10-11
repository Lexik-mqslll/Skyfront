package dev.krutlexik.skyfront.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.function.Supplier;

public class OpenRadarScreenPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenRadarScreenPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "OpenRadarScreenPacket".toLowerCase()));
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
