package dev.krutlexik.skyfront.drone;

import dev.krutlexik.skyfront.compat.sbw.SbwMonitor;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.network.StartControlPayload;
import dev.krutlexik.skyfront.port.PacketDistributor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Using a SuperbWarfare monitor that is paired with one of this mod's drones. */
@Mod.EventBusSubscriber(modid = DroneMod.MODID)
public final class MonitorControlEvents {
    @SubscribeEvent
    public static void onUseMonitor(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!SbwMonitor.isMonitor(stack) || !SbwMonitor.isLinked(stack) || !SbwMonitor.isFlaggedAta(stack)) {
            return;
        }

        event.setCanceled(true);
        if (event.getLevel().isClientSide) {
            return;
        }

        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer pilot) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (player.isShiftKeyDown()) {
            SbwMonitor.unlink(stack, player);
            player.displayClientMessage(Component.translatable("message.sfw.drone.unlinked"), true);
            return;
        }

        UUID linked = SbwMonitor.linkedDrone(stack);
        Entity entity = linked != null ? level.getEntity(linked) : null;
        if (entity != null && !(entity instanceof DroneEntity)) {
            SbwMonitor.clearSkyfrontFlag(stack);
            return;
        }
        if (entity == null && linked != null
                && DroneLinks.reconnectWhenLoaded(pilot, linked, MonitorControlEvents::connect)) {
            return;
        }
        if (!(entity instanceof DroneEntity drone) || drone.isRemoved()) {
            player.displayClientMessage(Component.translatable("message.sfw.drone.drone_missing"), true);
            return;
        }
        if (drone.isControlledBy(player)) {
            drone.releaseControl(false);
            return;
        }
        connect(pilot, drone);
    }

    /** Takes up the link to {@code drone}, if it can be taken: in reach, free, and charged. */
    private static void connect(ServerPlayer pilot, DroneEntity drone) {
        Player player = pilot;
        if (drone.isRemoved() || drone.isWrecked() || drone.distanceToSqr(player) > drone.controlRangeSqr()) {
            player.displayClientMessage(Component.translatable("message.sfw.drone.drone_missing"), true);
            return;
        }
        if (drone.isControlled()) {
            player.displayClientMessage(Component.translatable("message.sfw.drone.busy"), true);
            return;
        }
        if (drone.getCharge() <= 0.02F) {
            player.displayClientMessage(Component.translatable("message.sfw.drone.battery_empty"), true);
            return;
        }

        drone.setController(player.getUUID());
        drone.loadFlightChunks();
        PacketDistributor.sendToPlayer(pilot,
                dev.krutlexik.skyfront.drone.network.DroneTuningPayload.current());
        PacketDistributor.sendToPlayer(pilot, new StartControlPayload(drone.getId(), drone.controlRange(),
                drone.getX(), drone.getY(), drone.getZ(), drone.getYRot(), drone.getXRot(),
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getId(drone.getType()),
                drone.isArmed(), drone.getCharge()));
    }

    private MonitorControlEvents() {
    }
}
