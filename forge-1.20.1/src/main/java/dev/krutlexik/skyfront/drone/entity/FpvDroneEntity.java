package dev.krutlexik.skyfront.drone.entity;

import dev.krutlexik.skyfront.drone.GrenadeAttachments;
import dev.krutlexik.skyfront.drone.registry.DroneItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The FPV quad: a racing-quad airframe with a warhead strapped under it — an RPG round, the PG-7VM or the
 * Yasin 105 TBG, or a mortar bomb — flown into its target.
 */
public class FpvDroneEntity extends InterceptorDroneEntity {
    /** Closing speed into a block, blocks a tick, at which a round's contact fuse goes off. */
    private static final double FUSE_SPEED = 0.15D;

    public FpvDroneEntity(EntityType<? extends FpvDroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public Osd osdStyle() {
        return Osd.BETAFLIGHT_GPS;
    }

    /** An analog video link, as the Molniya's: clean up close, snow and tearing bands with range. */
    @Override
    public boolean analogVideo() {
        return true;
    }

    /** Its own top speed, not the interceptor's: 111 km/h flat out. */
    @Override
    public double topSpeedKmh() {
        return 111.0D;
    }

    /** Lighter than the interceptor for what it carries: more thrust over its weight... */
    @Override
    protected double thrustToWeight() {
        return super.thrustToWeight() * 1.3D;
    }

    /** ...and quicker to lean into a command and back out of it. */
    @Override
    protected double agility() {
        return 1.3D;
    }

    @Override
    public ItemStack packedItem() {
        return new ItemStack(DroneItems.FPV_DRONE.get());
    }

    @Override
    protected boolean carriesStores() {
        return true;
    }

    /** RPG rounds and the mortar bomb: warheads to be flown into something. */
    @Override
    protected boolean canCarry(ItemStack stack) {
        return GrenadeAttachments.isRocket(stack) || GrenadeAttachments.isMortarShell(stack);
    }

    @Override
    public boolean storesFollowAttitude() {
        return true;
    }

    private static final GrenadeAttachments.Mount PG7VM = new GrenadeAttachments.Mount(-0.11F, 1.37F, 0.0F, 0.0F,
            0.0F, 0.85F, new ResourceLocation("superbwarfare", "rpg_rocket_standard"));
    /** The mortar bomb slung nose first under the middle of the frame, its fins just short of the back. */
    private static final GrenadeAttachments.Mount MORTAR = new GrenadeAttachments.Mount(-0.065F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.9F, GrenadeAttachments.MORTAR_SHELL);
    private static final GrenadeAttachments.Mount YASIN = new GrenadeAttachments.Mount(-0.11F, 1.5F, 0.0F, 0.0F,
            0.0F, 0.85F, new ResourceLocation("superbwarfare", "rpg_rocket_tbg"));

    @Override
    public GrenadeAttachments.Mount mountFor(ItemStack stack) {
        GrenadeAttachments.Mount own = GrenadeAttachments.mountOf(stack);
        if (own.display() != null && own.display().getPath().equals("rpg_rocket_standard")) {
            return PG7VM;
        }
        if (own.display() != null && own.display().getPath().equals("rpg_rocket_tbg")) {
            return YASIN;
        }
        if (GrenadeAttachments.isMortarShell(stack)) {
            return MORTAR;
        }
        return own;
    }

    /**
     * How high the quad stands on its bricks, blocks: high enough for the round slung under it to clear the
     * ground (see {@link DroneStandEntity}).
     */
    public static final double STAND_HEIGHT = 0.225D;

    /** It is not the warhead: the round it carries is. */
    @Override
    protected void goOff(ServerLevel level, @Nullable Entity attacker) {
        if (!hasAttachment()) {
            GrenadeAttachments.breakUpHarmlessly(level, position());
            return;
        }
        goOffWithStore(level, attacker);
    }

    /** With a round aboard, the round's fuse decides: anything touched sets it off. */
    @Override
    protected double ramSpeed() {
        return hasAttachment() ? super.ramSpeed() : crashSpeed();
    }

    @Override
    protected float impactSpeed(Vec3 approach, @Nullable Ram ram) {
        if (hasAttachment() && ram == null && (this.horizontalCollision || this.verticalCollision)
                && approach.length() >= FUSE_SPEED) {
            return Math.nextUp((float) Math.max(approach.length(), crashSpeed()));
        }
        return super.impactSpeed(approach, ram);
    }

    /** A vehicle struck with a round aboard takes the round's own direct hit. */
    @Override
    protected float ramDamage() {
        if (!hasAttachment()) {
            return 0.0F;
        }
        float hit = GrenadeAttachments.hitDamage(getAttachment());
        return hit > 0.0F ? hit : super.ramDamage();
    }
}
