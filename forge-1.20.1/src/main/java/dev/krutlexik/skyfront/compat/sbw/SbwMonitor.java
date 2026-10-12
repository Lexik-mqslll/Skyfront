package dev.krutlexik.skyfront.compat.sbw;

import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** This mod's drones flown on SuperbWarfare's own remote — the monitor — instead of a controller of their own. */
public final class SbwMonitor {
    /** SBW's monitor item, by registry id — stable across SBW versions, unlike its class name. */
    private static final ResourceLocation MONITOR_ID =
            new ResourceLocation(SbwCompat.MOD_ID, "monitor");

    private static final String LINKED = "Linked";
    private static final String LINKED_DRONE = "LinkedDrone";
    /** Marks a monitor whose {@code LinkedDrone} is a Skyfront drone. */
    private static final String SKYFRONT_FLAG = "SkyfrontDrone";

    private SbwMonitor() {
    }

    /**
     * The stack's tag, copied — the same storage SBW's {@code Monitor} reads through {@code getOrCreateTag}, so
     * both mods always see one another's link.
     */
    private static CompoundTag tagOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.copy() : new CompoundTag();
    }

    /** Stores the tag back outright. */
    private static void save(ItemStack stack, CompoundTag tag) {
        stack.setTag(tag);
    }

    /** A fresh monitor, or an empty stack where SuperbWarfare is not installed. */
    public static ItemStack newMonitor() {
        return BuiltInRegistries.ITEM.getOptional(MONITOR_ID).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public static boolean isMonitor(ItemStack stack) {
        return !stack.isEmpty() && MONITOR_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Whether this monitor holds a link at all, to anyone's drone. */
    public static boolean isLinked(ItemStack stack) {
        return isMonitor(stack) && tagOf(stack).getBoolean(LINKED);
    }

    /** Whether this monitor's link was made to one of this mod's drones. */
    public static boolean isFlaggedAta(ItemStack stack) {
        return isMonitor(stack) && tagOf(stack).getBoolean(SKYFRONT_FLAG);
    }

    /** Whether this monitor is paired with exactly this drone. */
    public static boolean isLinkedTo(ItemStack stack, DroneEntity drone) {
        return isMonitor(stack) && drone.getStringUUID().equals(tagOf(stack).getString(LINKED_DRONE));
    }

    /** The UUID this monitor is paired with, or null if the tag is absent or unreadable. */
    @Nullable
    public static UUID linkedDrone(ItemStack stack) {
        if (!isMonitor(stack)) {
            return null;
        }
        String id = tagOf(stack).getString(LINKED_DRONE);
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException unparseable) {
            return null;
        }
    }

    /** Pairs the monitor with the drone, through SBW's own tag layout plus our marker. */
    public static void link(ItemStack stack, DroneEntity drone) {
        CompoundTag tag = tagOf(stack);
        tag.putBoolean(LINKED, true);
        tag.putString(LINKED_DRONE, drone.getStringUUID());
        tag.putBoolean(SKYFRONT_FLAG, true);
        save(stack, tag);
    }

    /**
     * Breaks the pairing the way SBW's own {@code disLink} writes it ({@code Linked} false, the drone id set
     * to {@code "none"}), and drops our marker.
     */
    public static void unlink(ItemStack stack, Player player) {
        CompoundTag tag = tagOf(stack);
        tag.putBoolean(LINKED, false);
        tag.putString(LINKED_DRONE, "none");
        tag.remove(SKYFRONT_FLAG);
        save(stack, tag);
    }

    /** Drops just our marker. */
    public static void clearSkyfrontFlag(ItemStack stack) {
        CompoundTag tag = tagOf(stack);
        tag.remove(SKYFRONT_FLAG);
        save(stack, tag);
    }

    /** Unlinks every monitor in {@code player}'s inventory that is paired with {@code drone}. */
    public static void unlinkMatching(Player player, DroneEntity drone) {
        String id = drone.getStringUUID();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isMonitor(stack) && id.equals(tagOf(stack).getString(LINKED_DRONE))) {
                unlink(stack, player);
            }
        }
    }

    /**
     * Frees every monitor on the server that is paired with a drone which has just left the world for good —
     * not merely the one its pilot is holding.
     */
    public static void unlinkEverywhere(DroneEntity drone) {
        var server = drone.level().getServer();
        if (server == null) {
            return;
        }
        for (Player player : server.getPlayerList().getPlayers()) {
            unlinkMatching(player, drone);
        }
    }
}
