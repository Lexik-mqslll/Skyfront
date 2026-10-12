package dev.krutlexik.skyfront.entity;

import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity;
import dev.krutlexik.skyfront.drone.entity.MolniyaDroneEntity;
import dev.krutlexik.skyfront.drone.registry.DroneEntities;
import dev.krutlexik.skyfront.drone.registry.DroneItems;
import dev.krutlexik.skyfront.item.ModItems;
import dev.krutlexik.skyfront.item.ShahedItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A launch rail for the Shahed and the Molniya: a beige steel frame carrying two aluminium rails that rise
 * toward the way it was set down facing.
 */
public class LauncherEntity extends Entity {
    /** The model is drawn this many times its authored size: rails 3.1 blocks long, 0.77 apart, the head 1.65 up. */
    public static final float SCALE = 1.2F;
    /** The middle of the model's footprint, which sits on the entity's position. */
    public static final double CENTRE_X = 8.0D;
    public static final double CENTRE_Z = 10.705D;
    /** The top edge of the rails, low end and high end (z along the rails, y up). */
    private static final double RAIL_LOW_Z = -8.834D;
    private static final double RAIL_LOW_Y = 13.161D;
    private static final double RAIL_HIGH_Z = 31.194D;
    private static final double RAIL_HIGH_Y = 22.035D;
    private static final double RAIL_LENGTH = 41.0D;
    /** How steeply the rails climb, degrees. */
    public static final float RAIL_PITCH = 12.5F;

    /** Where along the rails (0 at the low end, 1 at the high) each drone's own origin is laid. */
    private static final double SHAHED_SEAT = 0.53D;
    private static final double MOLNIYA_SEAT = 0.5D;

    /** Knocks it takes to break by hand, as damage: about as many as a boat. */
    private static final float BREAK_DAMAGE = 8.0F;

    @Nullable
    private UUID occupant;
    private float damage;

    public LauncherEntity(EntityType<? extends LauncherEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.LAUNCHER.get());
    }

    /** A point on the rails' top edge, in the world: {@code along} 0 at their foot, 1 at their head. */
    public Vec3 railPoint(double along) {
        double s = SCALE / 16.0D;
        double z = (Mth.lerp(along, RAIL_LOW_Z, RAIL_HIGH_Z) - CENTRE_Z) * s;
        double y = Mth.lerp(along, RAIL_LOW_Y, RAIL_HIGH_Y) * s;
        double yaw = Math.toRadians(getYRot());
        return position().add(-Math.sin(yaw) * z, y, Math.cos(yaw) * z);
    }

    /** The rail still ahead of a point {@code along} the way, in blocks. */
    public double railAhead(double along) {
        return (1.0D - along) * RAIL_LENGTH * SCALE / 16.0D;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (level() instanceof ServerLevel) {
                packUp(player);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        ItemStack stack = player.getItemInHand(hand);
        boolean shahed = stack.getItem() instanceof ShahedItem;
        boolean molniya = stack.is(DroneItems.MOLNIYA_DRONE.get());
        if (!shahed && !molniya) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (occupied(server)) {
            player.displayClientMessage(Component.translatable("msg.sfw.launcher.occupied")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        Entity placed;
        if (shahed) {
            ShahedEntity drone = new ShahedEntity(ModEntities.SHAHED.get(), server);
            ShahedItem.applyStackData(drone, server, stack);
            drone.seatOnRail(railPoint(SHAHED_SEAT).add(0.0D, 0.02D, 0.0D), -getYRot(), RAIL_PITCH,
                    railAhead(SHAHED_SEAT));
            placed = drone;
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            MolniyaDroneEntity drone = DroneEntities.MOLNIYA_DRONE.get().create(server);
            if (drone == null) {
                return InteractionResult.FAIL;
            }
            drone.seatOnRail(railPoint(MOLNIYA_SEAT).add(0.0D, 0.02D, 0.0D), getYRot(), RAIL_PITCH);
            placed = drone;
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        server.addFreshEntity(placed);
        this.occupant = placed.getUUID();
        return InteractionResult.CONSUME;
    }

    /** Whether the drone last laid on it is still there, still sitting on these rails. */
    private boolean occupied(ServerLevel server) {
        return seatedOccupant(server) != null;
    }

    /** The drone sitting on these rails, if one still is — launched or gone, it is forgotten. */
    @Nullable
    private Entity seatedOccupant(ServerLevel server) {
        if (this.occupant == null) {
            return null;
        }
        Entity there = server.getEntity(this.occupant);
        boolean seated = there instanceof ShahedEntity shahed && shahed.isOnRail()
                || there instanceof InterceptorDroneEntity plane && plane.isRailSeated();
        if (!seated || there.distanceToSqr(this) > 16.0D) {
            this.occupant = null;
            return null;
        }
        return there;
    }

    /**
     * Back into {@code player}'s inventory one thing at a time: the drone on the rails if there is one, and
     * the rail itself only once it stands empty.
     */
    public void packUp(Player player) {
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        Entity drone = seatedOccupant(server);
        this.occupant = null;
        if (drone instanceof ShahedEntity shahed) {
            giveOrDrop(player, shahed.toItem());
            shahed.discardAsItem();
            return;
        }
        if (drone instanceof DroneEntity plane) {
            plane.packUpFor(player, true);
            return;
        }
        giveOrDrop(player, new ItemStack(ModItems.LAUNCHER.get()));
        discard();
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** The rail is gone from under it: the drone on it falls off as its item. */
    private void dropOccupant() {
        if (level() instanceof ServerLevel server) {
            Entity drone = seatedOccupant(server);
            this.occupant = null;
            if (drone instanceof ShahedEntity shahed) {
                shahed.spawnAtLocation(shahed.toItem());
                shahed.discardAsItem();
            } else if (drone instanceof DroneEntity plane) {
                plane.dropAsItem();
            }
        }
        this.occupant = null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) {
            return false;
        }
        if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
            breakApart(false);
            return true;
        }
        this.damage += amount;
        markHurt();
        if (this.damage >= BREAK_DAMAGE) {
            breakApart(true);
        }
        return true;
    }

    private void breakApart(boolean drop) {
        dropOccupant();
        if (drop) {
            spawnAtLocation(new ItemStack(ModItems.LAUNCHER.get()));
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) {
            dropOccupant();
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.occupant = tag.hasUUID("Occupant") ? tag.getUUID("Occupant") : null;
        this.damage = tag.getFloat("Damage");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.occupant != null) {
            tag.putUUID("Occupant", this.occupant);
        }
        tag.putFloat("Damage", this.damage);
    }
}
