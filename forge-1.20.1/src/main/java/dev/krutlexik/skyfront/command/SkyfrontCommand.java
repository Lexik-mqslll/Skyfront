package dev.krutlexik.skyfront.command;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.entity.ShahedEntity;
import dev.krutlexik.skyfront.worldmap.WorldMapData;
import dev.krutlexik.skyfront.worldmap.WorldMapScanner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SkyfrontCommand {
    private static final int TITLE = 0xFFFF55;
    private static final int LABEL = 0xAAAAAA;
    private static final int VALUE = 0x55FFFF;
    private static final int GOOD = 0x55FF55;
    private static final int BAD = 0xFF5555;

    private static MutableComponent onOff(boolean value) {
        return value ? Component.translatable("command.sfw.config.on").withStyle(s -> s.withColor(GOOD))
                     : Component.translatable("command.sfw.config.off").withStyle(s -> s.withColor(BAD));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> configCommand() {
        return Commands.literal("config")
                .executes(SkyfrontCommand::showConfig)
                .then(Commands.literal("reload").executes(SkyfrontCommand::reloadConfig))
                .then(Commands.literal("particles")
                        .executes(context -> {
                            say(context, line("command.sfw.config.particles",
                                    onOff(SkyfrontConfig.areExplosionParticlesEnabled())));
                            say(context, line("command.sfw.config.particle_mult",
                                    value(SkyfrontConfig.getParticleMultiplier())));
                            return 1;
                        })
                        .then(change("enable", () -> SkyfrontConfig.setExplosionParticlesEnabled(true),
                                "command.sfw.config.particles.enabled", GOOD))
                        .then(change("disable", () -> SkyfrontConfig.setExplosionParticlesEnabled(false),
                                "command.sfw.config.particles.disabled", BAD))
                        .then(Commands.literal("multiplier")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 500))
                                        .executes(context -> {
                                            int value = IntegerArgumentType.getInteger(context, "value");
                                            SkyfrontConfig.setParticleMultiplier(value);
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "command.sfw.config.particle_mult.set", value)
                                                    .withStyle(s -> s.withColor(GOOD)), true);
                                            return 1;
                                        }))))
                .then(toggle("blockDamage", "command.sfw.config.block_damage",
                        SkyfrontConfig::doExplosionsBreakBlocks, SkyfrontConfig::setExplosionsBreakBlocks))
                .then(toggle("sounds", "command.sfw.config.sounds",
                        SkyfrontConfig::areCustomExplosionSoundsEnabled, SkyfrontConfig::setCustomExplosionSoundsEnabled))
                .then(Commands.literal("zone")
                        .executes(context -> {
                            say(context, line("command.sfw.config.blacklist", blacklistState()));
                            say(context, line("command.sfw.config.whitelist", whitelistState()));
                            return 1;
                        })
                        .then(toggle("blacklist", "command.sfw.config.blacklist",
                                SkyfrontConfig::isBlacklistEnabled, SkyfrontConfig::setBlacklistEnabled))
                        .then(toggle("whitelist", "command.sfw.config.whitelist",
                                SkyfrontConfig::isWhitelistEnabled, SkyfrontConfig::setWhitelistEnabled)));
    }

    /** Re-reads the config file, takes what is valid in it, and sends the drones' figures out again. */
    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        SkyfrontConfig.ReloadResult result = SkyfrontConfig.reloadFromFile();
        if (!result.fileFound()) {
            context.getSource().sendFailure(Component.translatable("command.sfw.config.reload.missing",
                    "config/" + dev.krutlexik.skyfront.config.SkyfrontConfig.FILE_NAME));
            return 0;
        }
        dev.krutlexik.skyfront.drone.DroneTuningSync.broadcast(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable("command.sfw.config.reload.done",
                result.changed()).withStyle(s -> s.withColor(GOOD)), true);
        if (!result.rejected().isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.sfw.config.reload.rejected",
                    String.join(", ", result.rejected())));
        }
        return 1;
    }

    private static int showConfig(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.translatable("command.sfw.config.title")
                .withStyle(s -> s.withColor(TITLE)), false);
        say(context, line("command.sfw.config.particles", onOff(SkyfrontConfig.areExplosionParticlesEnabled())));
        say(context, line("command.sfw.config.particle_mult", value(SkyfrontConfig.getParticleMultiplier())));
        say(context, line("command.sfw.config.block_damage", onOff(SkyfrontConfig.doExplosionsBreakBlocks())));
        say(context, line("command.sfw.config.sounds", onOff(SkyfrontConfig.areCustomExplosionSoundsEnabled())));
        say(context, line("command.sfw.config.blacklist", blacklistState()));
        say(context, line("command.sfw.config.whitelist", whitelistState()));
        say(context, line("command.sfw.config.file", Component.literal("config/" + dev.krutlexik.skyfront.config.SkyfrontConfig.FILE_NAME)
                .withStyle(s -> s.withColor(VALUE))));
        return 1;
    }

    /** {@code name} on its own reports the setting; {@code name enable|disable} changes and saves it. */
    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name, String key,
                                                                     BooleanSupplier current,
                                                                     Consumer<Boolean> set) {
        return Commands.literal(name)
                .executes(context -> {
                    say(context, line(key, onOff(current.getAsBoolean())));
                    return 1;
                })
                .then(change("enable", () -> set.accept(true), key + ".enabled", GOOD))
                .then(change("disable", () -> set.accept(false), key + ".disabled", BAD));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, Runnable apply,
                                                                     String doneKey, int colour) {
        return Commands.literal(name).executes(context -> {
            apply.run();
            context.getSource().sendSuccess(() -> Component.translatable(doneKey)
                    .withStyle(s -> s.withColor(colour)), true);
            return 1;
        });
    }

    private static MutableComponent blacklistState() {
        return zoneState(SkyfrontConfig.isBlacklistEnabled(), SkyfrontConfig.FORBIDDEN_ZONES.get());
    }

    private static MutableComponent whitelistState() {
        return zoneState(SkyfrontConfig.isWhitelistEnabled(), SkyfrontConfig.ALLOWED_ZONES.get());
    }

    /** On or off, and how many zones the list holds — only the ones that parse, as the check counts them. */
    private static MutableComponent zoneState(boolean enabled, java.util.List<? extends String> zones) {
        int count = SkyfrontConfig.packZones(zones).length / 4;
        return onOff(enabled).append(Component.translatable("command.sfw.config.zone_count", count)
                .withStyle(s -> s.withColor(LABEL)));
    }

    private static MutableComponent value(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(s -> s.withColor(VALUE));
    }

    private static MutableComponent line(String key, Component argument) {
        return Component.translatable(key, argument).withStyle(s -> s.withColor(LABEL));
    }

    private static void say(CommandContext<CommandSourceStack> context, Component message) {
        context.getSource().sendSuccess(() -> message, false);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("skyfront")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("attack")
                        .then(Commands.literal("shahed")
                                .then(Commands.argument("start", Vec3Argument.vec3())
                                        .then(Commands.argument("target", BlockPosArgument.blockPos())
                                                .then(Commands.literal("low")
                                                        .executes(context -> {
                                                            Vec3 startPos = Vec3Argument.getVec3(context, "start");
                                                            BlockPos targetPos = BlockPosArgument.getBlockPos(context, "target");
                                                            ServerLevel level = context.getSource().getLevel();
                                                            preloadChunks(level, startPos, Vec3.atCenterOf(targetPos));
                                                            ShahedEntity shahed = new ShahedEntity(level, startPos, targetPos, "low");
                                                            level.addFreshEntity(shahed);
                                                            context.getSource().sendSuccess(() -> Component.translatable("command.sfw.shahed.low").withStyle(s -> s.withColor(0x55FF55)), true);
                                                            return 1;
                                                        }))
                                                .then(Commands.literal("high")
                                                        .executes(context -> {
                                                            Vec3 startPos = Vec3Argument.getVec3(context, "start");
                                                            BlockPos targetPos = BlockPosArgument.getBlockPos(context, "target");
                                                            ServerLevel level = context.getSource().getLevel();
                                                            preloadChunks(level, startPos, Vec3.atCenterOf(targetPos));
                                                            ShahedEntity shahed = new ShahedEntity(level, startPos, targetPos, "high");
                                                            level.addFreshEntity(shahed);
                                                            context.getSource().sendSuccess(() -> Component.translatable("command.sfw.shahed.high").withStyle(s -> s.withColor(0x55FF55)), true);
                                                            return 1;
                                                        }))))))
                .then(configCommand())
                .then(Commands.literal("map")
                        .then(Commands.literal("rescan")
                                .executes(context -> {
                                    ServerLevel level = context.getSource().getLevel();
                                    WorldMapData mapData = WorldMapData.get(level);
                                    mapData.clear();
                                    WorldMapScanner.getInstance().startScan(level);
                                    context.getSource().sendSuccess(() -> Component.literal("Map rescan started").withStyle(s -> s.withColor(0x55FF55)), true);
                                    return 1;
                                }))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    WorldMapScanner scanner = WorldMapScanner.getInstance();
                                    ServerLevel level = context.getSource().getLevel();
                                    WorldMapData mapData = WorldMapData.get(level);
                                    
                                    if (scanner.isScanning()) {
                                        context.getSource().sendSuccess(() -> Component.literal(
                                            String.format("Scanning: %d/%d chunks",
                                                scanner.getProgress(), scanner.getTotalChunks())
                                        ).withStyle(s -> s.withColor(0xFFFF55)), false);
                                    } else if (mapData.isScanComplete()) {
                                        context.getSource().sendSuccess(() -> Component.literal(
                                            String.format("Scan complete: %d chunk colors", mapData.getColorCount())
                                        ).withStyle(s -> s.withColor(0x55FF55)), false);
                                    } else {
                                        context.getSource().sendSuccess(() -> Component.literal("Map not scanned").withStyle(s -> s.withColor(0xFF5555)), false);
                                    }
                                    return 1;
                                }))));
    }
    
    /**
     * The drone now manages its own rolling chunk window in flight (see ShahedEntity#maintainFlightChunks) —
     * only the spawn chunk needs a short-lived kick so the freshly spawned entity starts ticking.
     */
    private static void preloadChunks(ServerLevel level, Vec3 from, Vec3 to) {
        dev.krutlexik.skyfront.util.ChunkLoadHelper.keepTicking(level,
                dev.krutlexik.skyfront.util.ChunkLoadHelper.chunkAt(from.x, from.z));
    }
}
