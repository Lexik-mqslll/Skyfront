package dev.krutlexik.skyfront.drone;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Which SuperbWarfare grenades can be slung under the drone and dropped. */
public final class GrenadeAttachments {
    public static final ResourceLocation RGO_GRENADE =
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "rgo_grenade");
    public static final ResourceLocation M18_SMOKE_GRENADE =
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "m18_smoke_grenade");
    public static final ResourceLocation RPG_ROCKET_TBG =
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "rpg_rocket_tbg");
    /** The PG-7VM: the RPG-7's standard HEAT round. */
    public static final ResourceLocation RPG_ROCKET_STANDARD =
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "rpg_rocket_standard");
    /** SuperbWarfare's mortar bomb: hung on the FPV quad as its warhead, nose first. */
    public static final ResourceLocation MORTAR_SHELL =
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "mortar_shell");
    public static final ResourceLocation C4_BOMB =
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "c4_bomb");

    /**
     * What a store does when it goes off, taken from SuperbWarfare's own drone-attachment data so ordnance
     * carried by our drone lands as hard as it does under theirs.
     */
    private record Warhead(float blast, float sbwDamage, float sbwRadius, int effect) {
    }

    @org.jetbrains.annotations.Nullable
    private static Warhead warhead(ResourceLocation id) {
        if (C4_BOMB.equals(id)) {
            return new Warhead(9.0F, 300.0F, 10.0F, 2);
        }
        if (RPG_ROCKET_TBG.equals(id)) {
            return new Warhead(9.0F, 150.0F, 11.0F, 2);
        }
        if (RPG_ROCKET_STANDARD.equals(id)) {
            return new Warhead(4.0F, 80.0F, 5.0F, 1);
        }
        if (RGO_GRENADE.equals(id)) {
            return new Warhead(5.0F, 130.0F, 10.0F, 0);
        }
        if (MORTAR_SHELL.equals(id)) {
            return new Warhead(6.0F, 100.0F, 8.0F, 1);
        }
        return null;
    }

    /** Everything the drone will carry: the two it can drop, plus the three it can only ram with. */
    public static boolean isAttachable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return RGO_GRENADE.equals(id) || M18_SMOKE_GRENADE.equals(id)
                || isRocket(stack) || C4_BOMB.equals(id);
    }

    /** One of the RPG-7's rounds: the PG-7VM or the Yasin 105 TBG. */
    public static boolean isRocket(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return RPG_ROCKET_STANDARD.equals(id) || RPG_ROCKET_TBG.equals(id);
    }

    /** SuperbWarfare's mortar bomb. */
    public static boolean isMortarShell(ItemStack stack) {
        return !stack.isEmpty() && MORTAR_SHELL.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * What a store does to a vehicle it is flown straight into, beyond its blast: SuperbWarfare's own {@code
     * HitDamage} for it — the shaped charge's jet, the thermobaric round's punch — or nothing for a store
     * that only bursts.
     */
    public static float hitDamage(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0F;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (RPG_ROCKET_STANDARD.equals(id)) {
            return 340.0F;
        }
        if (RPG_ROCKET_TBG.equals(id)) {
            return 250.0F;
        }
        if (MORTAR_SHELL.equals(id)) {
            return 60.0F;
        }
        return 0.0F;
    }

    /** Whether this store can be let go of in flight. */
    public static boolean isDroppable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return RGO_GRENADE.equals(id) || M18_SMOKE_GRENADE.equals(id);
    }

    /** Whether this store will actually detonate if the drone carrying it is destroyed. */
    public static boolean isExplosive(ItemStack stack) {
        return !stack.isEmpty() && warhead(BuiltInRegistries.ITEM.getKey(stack.getItem())) != null;
    }

    /** How a store sits on the pylon. */
    public record Mount(float y, float z, float yaw, float pitch, float roll, float scale,
                        @org.jetbrains.annotations.Nullable ResourceLocation display) {
    }

    private static final Mount BALL = new Mount(0.06F, 0.0F, 0.0F, 0.0F, 0.0F, 0.85F, null);
    private static final Mount CANISTER = new Mount(-0.02F, 0.0F, 0.0F, 90.0F, 0.0F, 0.85F, null);
    /**
     * The rocket, drawn from its own projectile model (warhead toward -Z, tail at the origin) and slung level
     * under the belly between the legs, the warhead under the body and the motor tube running out behind —
     * placed against a preview of the two together.
     */
    private static final Mount ROCKET = new Mount(-0.02F, 1.5F, 0.0F, 0.0F, 0.0F, 0.85F, RPG_ROCKET_TBG);
    /** The shorter PG-7VM, seated the same way with its tail further forward. */
    private static final Mount ROCKET_STANDARD =
            new Mount(-0.02F, 1.27F, 0.0F, 0.0F, 0.0F, 0.85F, RPG_ROCKET_STANDARD);
    /** The charge lying flat on the belly, turned across the airframe. */
    private static final Mount CHARGE = new Mount(0.10F, 0.0F, 90.0F, 0.0F, -180.0F, 0.9F,
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "c4"));

    /** Where and how this store hangs. */
    public static Mount mountOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return BALL;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (M18_SMOKE_GRENADE.equals(id)) {
            return CANISTER;
        }
        if (RPG_ROCKET_TBG.equals(id)) {
            return ROCKET;
        }
        if (RPG_ROCKET_STANDARD.equals(id)) {
            return ROCKET_STANDARD;
        }
        if (C4_BOMB.equals(id)) {
            return CHARGE;
        }
        return BALL;
    }

    /**
     * Sets the slung store off where it hangs — the drone flying into something at speed, or being shot out
     * of the sky with the fuse still live.
     */
    public static void detonate(ServerLevel level, ItemStack attachment, Vec3 at,
                                @org.jetbrains.annotations.Nullable Entity source,
                                @org.jetbrains.annotations.Nullable Entity attacker) {
        Warhead warhead = warhead(BuiltInRegistries.ITEM.getKey(attachment.getItem()));
        if (warhead == null) {
            return;
        }
        detonateWarhead(level, at, source, attacker,
                warhead.blast(), warhead.sbwDamage(), warhead.sbwRadius(), warhead.effect());
    }

    /** A warhead going off, with its figures given rather than looked up from a store. */
    public static void detonateWarhead(ServerLevel level, Vec3 at,
                                       @org.jetbrains.annotations.Nullable Entity source,
                                       @org.jetbrains.annotations.Nullable Entity attacker,
                                       float blast, float sbwDamage, float sbwRadius, int effect) {
        level.explode(source, blastDamage(level, source, attacker), null, at.x, at.y, at.z, blast, false,
                dev.krutlexik.skyfront.config.SkyfrontConfig.doExplosionsBreakBlocks()
                        ? Level.ExplosionInteraction.TNT
                        : Level.ExplosionInteraction.NONE);
        if (dev.krutlexik.skyfront.compat.sbw.SbwCompat.isLoaded()) {
            dev.krutlexik.skyfront.compat.sbw.SbwVehicles.spawnExplosionEffect(level, at, effect);
            if (source != null) {
                dev.krutlexik.skyfront.compat.sbw.SbwVehicles.damageVehiclesAround(
                        level, source, attacker, at, sbwDamage, sbwRadius);
            }
        }
    }

    /** The airframe itself letting go — a battery and four motors coming apart, not a warhead. */
    public static void breakUp(ServerLevel level, Vec3 at, @org.jetbrains.annotations.Nullable Entity source,
                               @org.jetbrains.annotations.Nullable Entity attacker) {
        level.explode(source, blastDamage(level, source, attacker), null, at.x, at.y, at.z, 1.0F, false,
                Level.ExplosionInteraction.NONE);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                at.x, at.y, at.z, 12, 0.3D, 0.2D, 0.3D, 0.02D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                at.x, at.y, at.z, 8, 0.25D, 0.2D, 0.25D, 0.05D);
    }

    /** A drone's blast, credited to whoever set it off: the pilot who flew it in, or whoever shot it down. */
    private static net.minecraft.world.damagesource.DamageSource blastDamage(
            ServerLevel level, @org.jetbrains.annotations.Nullable Entity source,
            @org.jetbrains.annotations.Nullable Entity attacker) {
        return level.damageSources().explosion(source, attacker);
    }

    /**
     * An airframe coming apart with nothing aboard to go off: the crack and the smoke of it, and no blast —
     * nothing near it is hurt.
     */
    public static void breakUpHarmlessly(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 1.6F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                at.x, at.y, at.z, 12, 0.3D, 0.2D, 0.3D, 0.02D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                at.x, at.y, at.z, 8, 0.25D, 0.2D, 0.25D, 0.05D);
    }

    /** Whether this is the smoke canister, which alone among the stores carries a fuse setting. */
    public static boolean isSmokeCanister(ItemStack stack) {
        return !stack.isEmpty() && M18_SMOKE_GRENADE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** How much of the drone's own travel the released store carries with it, as SuperbWarfare does. */
    private static final double INHERITED_MOTION = 0.2D;
    /** A nudge down on top of that, so the store always leaves a drone that is itself descending. */
    private static final double SEPARATION_SPEED = 0.15D;

    /** Ticks the released smoke waits before its cloud starts. */
    private static final int SMOKE_FUSE_TICKS = 40;

    /**
     * Releases the slung store, built the way SuperbWarfare's own drone drops its attachments ({@code
     * DroneEntity.droneDrop}): the projectile is built from the registry by id, handed its owner afterwards,
     * and then placed and pushed.
     */
    public static boolean release(ServerLevel level, @org.jetbrains.annotations.Nullable ServerPlayer pilot,
                                  ItemStack attachment, Vec3 pos, Vec3 droneVelocity) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(attachment.getItem());
        Entity store = EntityType.byString(id.toString()).map(type -> type.create(level)).orElse(null);
        if (store == null) {
            return false;
        }

        if (store instanceof Projectile projectile) {
            projectile.setOwner(pilot);
        }

        if (isSmokeCanister(attachment)) {
            CompoundTag dropData = new CompoundTag();
            dropData.putInt("Fuse", SMOKE_FUSE_TICKS);
            store.load(dropData);
        }

        store.setPos(pos.x, pos.y, pos.z);

        Vec3 motion = new Vec3(
                droneVelocity.x * INHERITED_MOTION,
                Math.min(droneVelocity.y, 0.0D) * INHERITED_MOTION - SEPARATION_SPEED,
                droneVelocity.z * INHERITED_MOTION);
        store.setDeltaMovement(motion);

        float yaw = (float) (Mth.atan2(motion.x, motion.z) * (180.0F / (float) Math.PI));
        float pitch = (float) (Mth.atan2(motion.y, motion.horizontalDistance()) * (180.0F / (float) Math.PI));
        store.setYRot(yaw);
        store.setXRot(pitch);
        store.yRotO = yaw;
        store.xRotO = pitch;

        return level.addFreshEntity(store);
    }

    private GrenadeAttachments() {
    }
}
