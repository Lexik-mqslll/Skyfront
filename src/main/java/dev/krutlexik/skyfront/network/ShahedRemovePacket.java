package dev.krutlexik.skyfront.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.resources.ResourceLocation;
import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.function.Supplier;

public class ShahedRemovePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShahedRemovePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "ShahedRemovePacket".toLowerCase()));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShahedRemovePacket> STREAM_CODEC = StreamCodec.ofMember(ShahedRemovePacket::toBytes, ShahedRemovePacket::new);
    @Override
    public CustomPacketPayload.Type<ShahedRemovePacket> type() { return TYPE; }
    private final int entityId;

    public ShahedRemovePacket(int entityId) {
        this.entityId = entityId;
    }

    public ShahedRemovePacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleShahedRemove(this.entityId));
    }

    public int getEntityId() {
        return this.entityId;
    }
}
