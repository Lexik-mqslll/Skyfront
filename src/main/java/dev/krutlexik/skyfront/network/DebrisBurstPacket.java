package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.entity.DebrisEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** An airframe coming apart: every piece as the blast threw it (see {@link DebrisEntity#plan}). */
public record DebrisBurstPacket(List<DebrisEntity.Spawn> pieces) implements CustomPacketPayload {
    public static final Type<DebrisBurstPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "debris_burst"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DebrisBurstPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeVarInt(packet.pieces.size());
                for (DebrisEntity.Spawn spawn : packet.pieces) {
                    buf.writeVarInt(spawn.piece().ordinal());
                    buf.writeDouble(spawn.position().x);
                    buf.writeDouble(spawn.position().y);
                    buf.writeDouble(spawn.position().z);
                    buf.writeFloat((float) spawn.velocity().x);
                    buf.writeFloat((float) spawn.velocity().y);
                    buf.writeFloat((float) spawn.velocity().z);
                    buf.writeFloat(spawn.yRot());
                    buf.writeQuaternion(spawn.attitude());
                    buf.writeVector3f(spawn.spin());
                }
            }, buf -> {
                int count = buf.readVarInt();
                List<DebrisEntity.Spawn> pieces = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    DebrisEntity.Piece piece = DebrisEntity.Piece.of(buf.readVarInt());
                    Vec3 position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                    Vec3 velocity = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
                    float yRot = buf.readFloat();
                    Quaternionf attitude = buf.readQuaternion();
                    Vector3f spin = buf.readVector3f();
                    pieces.add(new DebrisEntity.Spawn(piece, position, velocity, yRot, attitude, spin));
                }
                return new DebrisBurstPacket(pieces);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DebrisBurstPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> dev.krutlexik.skyfront.client.DebrisClient.spawn(packet.pieces()));
    }
}
