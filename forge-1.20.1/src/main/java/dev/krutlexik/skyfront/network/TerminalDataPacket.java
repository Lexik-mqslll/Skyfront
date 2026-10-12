package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.block.entity.ShahedTerminalBlockEntity;
import dev.krutlexik.skyfront.entity.ShahedEntity;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.IPayloadContext;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;
import dev.krutlexik.skyfront.port.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server -&gt; client: the terminal's bound-drone list with live statuses. */
public class TerminalDataPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerminalDataPacket> TYPE = new CustomPacketPayload.Type<>(
            new ResourceLocation(SkyfrontMod.MOD_ID, "terminaldatapacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalDataPacket> STREAM_CODEC =
            StreamCodec.ofMember(TerminalDataPacket::toBytes, TerminalDataPacket::new);

    public static final int STATUS_READY = 0;
    public static final int STATUS_FLIGHT = 1;
    public static final int STATUS_DOWN = 2;
    public static final int STATUS_NO_SIGNAL = 3;

    public record Row(int number, UUID uuid, int status, int ex, int ey, int ez,
                      int lx, int lz, float yaw, int altitude, boolean farDive, int[] route) {
    }

    private final BlockPos terminalPos;
    private final boolean openScreen;
    private final int maxWaypoints;
    private final int minSpacing;
    private final int minLaunchDistance;
    private final int maxAltitude;
    private final boolean whitelistEnabled;
    private final int[] whitelistZones;
    private final boolean blacklistEnabled;
    private final int[] blacklistZones;
    private final List<Row> rows;

    public TerminalDataPacket(BlockPos terminalPos, boolean openScreen, int maxWaypoints, int minSpacing,
                              int minLaunchDistance, int maxAltitude,
                              boolean whitelistEnabled, int[] whitelistZones,
                              boolean blacklistEnabled, int[] blacklistZones, List<Row> rows) {
        this.terminalPos = terminalPos;
        this.openScreen = openScreen;
        this.maxWaypoints = maxWaypoints;
        this.minSpacing = minSpacing;
        this.minLaunchDistance = minLaunchDistance;
        this.maxAltitude = maxAltitude;
        this.whitelistEnabled = whitelistEnabled;
        this.whitelistZones = whitelistZones;
        this.blacklistEnabled = blacklistEnabled;
        this.blacklistZones = blacklistZones;
        this.rows = rows;
    }

    public TerminalDataPacket(RegistryFriendlyByteBuf buf) {
        this.terminalPos = buf.readBlockPos();
        this.openScreen = buf.readBoolean();
        this.maxWaypoints = buf.readVarInt();
        this.minSpacing = buf.readVarInt();
        this.minLaunchDistance = buf.readVarInt();
        this.maxAltitude = buf.readVarInt();
        this.whitelistEnabled = buf.readBoolean();
        this.whitelistZones = buf.readVarIntArray();
        this.blacklistEnabled = buf.readBoolean();
        this.blacklistZones = buf.readVarIntArray();
        int size = buf.readInt();
        this.rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.rows.add(new Row(buf.readInt(), buf.readUUID(), buf.readByte(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readFloat(),
                    buf.readInt(), buf.readBoolean(), buf.readVarIntArray()));
        }
    }

    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.terminalPos);
        buf.writeBoolean(this.openScreen);
        buf.writeVarInt(this.maxWaypoints);
        buf.writeVarInt(this.minSpacing);
        buf.writeVarInt(this.minLaunchDistance);
        buf.writeVarInt(this.maxAltitude);
        buf.writeBoolean(this.whitelistEnabled);
        buf.writeVarIntArray(this.whitelistZones);
        buf.writeBoolean(this.blacklistEnabled);
        buf.writeVarIntArray(this.blacklistZones);
        buf.writeInt(this.rows.size());
        for (Row row : this.rows) {
            buf.writeInt(row.number());
            buf.writeUUID(row.uuid());
            buf.writeByte(row.status());
            buf.writeInt(row.ex());
            buf.writeInt(row.ey());
            buf.writeInt(row.ez());
            buf.writeInt(row.lx());
            buf.writeInt(row.lz());
            buf.writeFloat(row.yaw());
            buf.writeInt(row.altitude());
            buf.writeBoolean(row.farDive());
            buf.writeVarIntArray(row.route());
        }
    }

    @Override
    public CustomPacketPayload.Type<TerminalDataPacket> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> dev.krutlexik.skyfront.client.ClientProxy
                .handleTerminalData(this.terminalPos, this.openScreen, this.maxWaypoints, this.minSpacing,
                        this.minLaunchDistance, this.maxAltitude, this.whitelistEnabled, this.whitelistZones,
                        this.blacklistEnabled, this.blacklistZones, this.rows));
    }

    /** Builds the current status snapshot for a terminal (server side). */
    public static TerminalDataPacket build(ServerLevel level, BlockPos pos,
                                           ShahedTerminalBlockEntity terminal, boolean openScreen) {
        List<Row> rows = new ArrayList<>();
        for (ShahedTerminalBlockEntity.Entry entry : terminal.getEntries()) {
            int status = STATUS_NO_SIGNAL;
            int ex = 0;
            int ey = 0;
            int ez = 0;
            int lx = pos.getX();
            int lz = pos.getZ();
            float yaw = 0.0f;
            Entity entity = level.getEntity(entry.uuid);
            if (entity instanceof ShahedEntity shahed && shahed.isAlive()) {
                ex = (int) Math.round(shahed.getX());
                ey = (int) Math.round(shahed.getY());
                ez = (int) Math.round(shahed.getZ());
                yaw = shahed.getYRot();
                Vec3 origin = shahed.getLaunchOrigin();
                lx = (int) Math.round(origin != null ? origin.x : shahed.getX());
                lz = (int) Math.round(origin != null ? origin.z : shahed.getZ());
                if (shahed.getIsFalling()) {
                    status = STATUS_DOWN;
                } else if (shahed.isParked()) {
                    status = STATUS_READY;
                } else {
                    status = STATUS_FLIGHT;
                }
            }
            rows.add(new Row(entry.number, entry.uuid, status, ex, ey, ez, lx, lz, yaw,
                    entry.altitude, entry.farDive, entry.route));
        }
        return new TerminalDataPacket(pos, openScreen,
                dev.krutlexik.skyfront.config.SkyfrontConfig.getMaxWaypoints(),
                dev.krutlexik.skyfront.config.SkyfrontConfig.getMinWaypointDistance(),
                dev.krutlexik.skyfront.config.SkyfrontConfig.MIN_LAUNCH_DISTANCE.get(),
                dev.krutlexik.skyfront.config.SkyfrontConfig.getMaxAltitude(),
                dev.krutlexik.skyfront.config.SkyfrontConfig.isWhitelistEnabled(),
                dev.krutlexik.skyfront.config.SkyfrontConfig.packZones(
                        dev.krutlexik.skyfront.config.SkyfrontConfig.ALLOWED_ZONES.get()),
                dev.krutlexik.skyfront.config.SkyfrontConfig.isBlacklistEnabled(),
                dev.krutlexik.skyfront.config.SkyfrontConfig.packZones(
                        dev.krutlexik.skyfront.config.SkyfrontConfig.FORBIDDEN_ZONES.get()),
                rows);
    }
}
