package dev.krutlexik.skyfront.network;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.block.entity.ShahedTerminalBlockEntity;
import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.entity.ShahedEntity;
import dev.krutlexik.skyfront.port.CustomPacketPayload;
import dev.krutlexik.skyfront.port.IPayloadContext;
import dev.krutlexik.skyfront.port.RegistryFriendlyByteBuf;
import dev.krutlexik.skyfront.port.StreamCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Client -&gt; server: terminal GUI actions. */
public class TerminalActionPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerminalActionPacket> TYPE = new CustomPacketPayload.Type<>(
            new ResourceLocation(SkyfrontMod.MOD_ID, "terminalactionpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalActionPacket> STREAM_CODEC =
            StreamCodec.ofMember(TerminalActionPacket::toBytes, TerminalActionPacket::new);

    /** Absolute engine cap on waypoints, regardless of config. */
    public static final int HARD_MAX_WAYPOINTS = 15;

    public static final int ACTION_REFRESH = 0;
    public static final int ACTION_SET_ROUTE = 1;
    public static final int ACTION_LAUNCH = 2;
    public static final int ACTION_UNBIND = 3;

    private final BlockPos terminalPos;
    private final int action;
    private final UUID uuid;
    private final int[] route;
    private final int altitude;
    private final boolean farDive;

    public TerminalActionPacket(BlockPos terminalPos, int action, UUID uuid, int[] route, int altitude, boolean farDive) {
        this.terminalPos = terminalPos;
        this.action = action;
        this.uuid = uuid;
        this.route = route;
        this.altitude = altitude;
        this.farDive = farDive;
    }

    public static TerminalActionPacket refresh(BlockPos terminalPos) {
        return new TerminalActionPacket(terminalPos, ACTION_REFRESH, new UUID(0L, 0L), new int[0], 0, true);
    }

    public static TerminalActionPacket unbind(BlockPos terminalPos, UUID uuid) {
        return new TerminalActionPacket(terminalPos, ACTION_UNBIND, uuid, new int[0], 0, true);
    }

    public TerminalActionPacket(RegistryFriendlyByteBuf buf) {
        this.terminalPos = buf.readBlockPos();
        this.action = buf.readByte();
        this.uuid = buf.readUUID();
        this.route = buf.readVarIntArray();
        this.altitude = buf.readInt();
        this.farDive = buf.readBoolean();
    }

    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.terminalPos);
        buf.writeByte(this.action);
        buf.writeUUID(this.uuid);
        buf.writeVarIntArray(this.route);
        buf.writeInt(this.altitude);
        buf.writeBoolean(this.farDive);
    }

    @Override
    public CustomPacketPayload.Type<TerminalActionPacket> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.position().distanceToSqr(this.terminalPos.getCenter()) > 8.0 * 8.0) {
                return;
            }
            if (!(level.getBlockEntity(this.terminalPos) instanceof ShahedTerminalBlockEntity terminal)) {
                return;
            }

            switch (this.action) {
                case ACTION_SET_ROUTE -> this.applyRoute(level, player, terminal, true);
                case ACTION_LAUNCH -> {
                    if (this.applyRoute(level, player, terminal, true)) {
                        this.launch(level, player, terminal);
                    }
                }
                case ACTION_UNBIND -> terminal.unbind(this.uuid);
                default -> {
                }
            }

            NetworkHandler.sendToPlayer(player, TerminalDataPacket.build(level, this.terminalPos, terminal, false));
        });
    }

    /** Validates and stores the route; when {@code strict}, failures message the player. */
    private boolean applyRoute(ServerLevel level, ServerPlayer player, ShahedTerminalBlockEntity terminal, boolean strict) {
        ShahedTerminalBlockEntity.Entry entry = terminal.findByUuid(this.uuid);
        if (entry == null || this.route.length % 2 != 0) {
            return false;
        }
        int maxPoints = SkyfrontConfig.getMaxWaypoints();
        if (this.route.length / 2 > maxPoints) {
            if (strict) {
                player.sendSystemMessage(Component.translatable("msg.sfw.route.too_many", maxPoints).withStyle(ChatFormatting.RED));
            }
            return false;
        }
        int minSpacing = SkyfrontConfig.getMinWaypointDistance();
        for (int i = 2; i < this.route.length; i += 2) {
            double dx = this.route[i] - this.route[i - 2];
            double dz = this.route[i + 1] - this.route[i - 1];
            if (dx * dx + dz * dz < (double) minSpacing * minSpacing) {
                if (strict) {
                    player.sendSystemMessage(Component.translatable("msg.sfw.route.too_close", minSpacing).withStyle(ChatFormatting.RED));
                }
                return false;
            }
        }
        for (int i = 0; i < this.route.length; i += 2) {
            if (SkyfrontConfig.isInForbiddenZone(this.route[i], this.route[i + 1])) {
                if (strict) {
                    player.sendSystemMessage(Component.translatable("msg.sfw.route.forbidden_zone")
                            .withStyle(ChatFormatting.RED));
                }
                return false;
            }
        }

        Entity droneEntity = level.getEntity(this.uuid);
        int minLaunch = SkyfrontConfig.MIN_LAUNCH_DISTANCE.get();
        if (droneEntity != null && minLaunch > 0) {
            for (int i = 0; i < this.route.length; i += 2) {
                double dx = this.route[i] - droneEntity.getX();
                double dz = this.route[i + 1] - droneEntity.getZ();
                if (dx * dx + dz * dz < (double) minLaunch * minLaunch) {
                    if (strict) {
                        player.sendSystemMessage(Component.translatable("msg.sfw.route.too_close_launch", minLaunch)
                                .withStyle(ChatFormatting.RED));
                    }
                    return false;
                }
            }
        }

        entry.route = this.route.clone();
        entry.altitude = Mth.clamp(this.altitude, SkyfrontConfig.MIN_ALTITUDE, SkyfrontConfig.getMaxAltitude());
        entry.farDive = this.farDive;
        terminal.setChanged();
        return true;
    }

    private void launch(ServerLevel level, ServerPlayer player, ShahedTerminalBlockEntity terminal) {
        ShahedTerminalBlockEntity.Entry entry = terminal.findByUuid(this.uuid);
        if (entry == null) {
            return;
        }
        if (entry.waypointCount() == 0) {
            player.sendSystemMessage(Component.translatable("msg.sfw.route.empty").withStyle(ChatFormatting.RED));
            return;
        }
        int targetX = entry.route[entry.route.length - 2];
        int targetZ = entry.route[entry.route.length - 1];
        if (SkyfrontConfig.isInForbiddenZone(targetX, targetZ)) {
            player.sendSystemMessage(Component.translatable("chat.sfw.error.forbidden").withStyle(ChatFormatting.RED));
            return;
        }
        Entity entity = level.getEntity(entry.uuid);
        if (!(entity instanceof ShahedEntity shahed) || !shahed.isAlive() || !shahed.isParked()) {
            player.sendSystemMessage(Component.translatable("msg.sfw.launch.not_ready").withStyle(ChatFormatting.RED));
            return;
        }
        int maxRange = SkyfrontConfig.MAX_FLIGHT_RANGE.get();
        for (int i = 0; i < entry.route.length; i += 2) {
            double dx = entry.route[i] - shahed.getX();
            double dz = entry.route[i + 1] - shahed.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > maxRange) {
                player.sendSystemMessage(Component.translatable("msg.sfw.launch.too_far",
                        (int) distance, maxRange).withStyle(ChatFormatting.RED));
                return;
            }
        }
        shahed.setOwner(player);
        shahed.launchFromGround(entry.route, entry.altitude, entry.farDive ? "high" : "low");
        Component prefix = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("SFW").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(prefix.copy().append(
                Component.translatable("msg.sfw.launch.success", entry.number, targetX, targetZ)
                        .withStyle(ChatFormatting.GREEN)));
    }
}
