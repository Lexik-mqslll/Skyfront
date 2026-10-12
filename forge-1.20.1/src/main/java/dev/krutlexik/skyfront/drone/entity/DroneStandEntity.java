package dev.krutlexik.skyfront.drone.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The two bricks an FPV quad is set down on, so the round slung under it clears the ground. */
public class DroneStandEntity extends Entity {
    /** How long the bricks lie about once the quad has left them. */
    public static final int LINGER_TICKS = 100;
    /** How long after loading the bricks wait for their quad to load in as well. */
    private static final int LOAD_GRACE_TICKS = 40;
    /** How high over the bricks' top the quad can sit and still count as on them. */
    private static final double ON_TOP = 0.15D;

    @Nullable
    private UUID drone;
    /** Ticks since the quad left, or -1 while it is still sitting on the bricks. */
    private int leftTicks = -1;

    public DroneStandEntity(EntityType<? extends DroneStandEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    /** The quad these bricks were put down with. */
    public void holding(DroneEntity drone) {
        this.drone = drone.getUUID();
        setYRot(drone.getYRot());
        this.yRotO = getYRot();
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Entity quad = this.drone != null ? level.getEntity(this.drone) : null;
        if (quad == null || quad.isRemoved()) {
            if (this.tickCount > LOAD_GRACE_TICKS) {
                discard();
            }
            return;
        }
        if (carrying(quad)) {
            this.leftTicks = -1;
        } else if (this.leftTicks < 0) {
            this.leftTicks = 0;
        } else if (++this.leftTicks >= LINGER_TICKS) {
            discard();
        }
    }

    /** Whether the quad is sitting on these bricks: over them, and no higher than just on top. */
    private boolean carrying(Entity quad) {
        AABB top = getBoundingBox();
        AABB box = quad.getBoundingBox();
        return box.minY <= top.maxY + ON_TOP && box.maxY >= top.minY
                && box.maxX > top.minX && box.minX < top.maxX
                && box.maxZ > top.minZ && box.minZ < top.maxZ;
    }

    /** Solid, so the quad stands on it as on a block. */
    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    /** Not a thing to be clicked: a click aimed at the quad goes to the quad. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.drone = tag.hasUUID("Drone") ? tag.getUUID("Drone") : null;
        this.leftTicks = tag.contains("LeftTicks") ? tag.getInt("LeftTicks") : -1;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.drone != null) {
            tag.putUUID("Drone", this.drone);
        }
        tag.putInt("LeftTicks", this.leftTicks);
    }
}
