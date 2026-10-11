package dev.krutlexik.skyfront.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The mod's common config, {@code config/skyfront-common.toml}. */
public class SkyfrontConfig {
    public static final String FILE_NAME = "skyfront-common.toml";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // targeting
    public static final ModConfigSpec.BooleanValue BLACKLIST_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORBIDDEN_ZONES;
    public static final ModConfigSpec.BooleanValue WHITELIST_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_ZONES;

    // explosions
    public static final ModConfigSpec.BooleanValue EXPLOSION_PARTICLES_ENABLED;
    public static final ModConfigSpec.IntValue EXPLOSION_PARTICLE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue EXPLOSION_BREAKS_BLOCKS;
    public static final ModConfigSpec.BooleanValue CUSTOM_EXPLOSION_SOUNDS;

    // shahed
    public static final ModConfigSpec.DoubleValue SHAHED_HEALTH;
    public static final ModConfigSpec.IntValue MAX_FLIGHT_RANGE;
    public static final ModConfigSpec.IntValue MAX_ROUTE_WAYPOINTS;
    public static final ModConfigSpec.IntValue MIN_WAYPOINT_DISTANCE;
    public static final ModConfigSpec.IntValue MIN_LAUNCH_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_ALTITUDE;

    // radar and siren
    public static final ModConfigSpec.IntValue RADAR_DETECT_RANGE;
    public static final ModConfigSpec.IntValue SIREN_RANGE;

    // all drones
    public static final ModConfigSpec.IntValue DRONE_CHUNK_RADIUS;
    public static final ModConfigSpec.IntValue DRONE_AIRBURST_CHANCE;
    public static final ModConfigSpec.DoubleValue DRONE_RAM_DAMAGE;

    // Mavic 3 Pro (the Mavic 3T shares everything but its own section)
    public static final ModConfigSpec.IntValue MAVIC_FLIGHT_SECONDS;
    public static final ModConfigSpec.IntValue MAVIC_CONTROL_RANGE;
    public static final ModConfigSpec.DoubleValue MAVIC_CRASH_SPEED;
    public static final ModConfigSpec.DoubleValue MAVIC_MAX_DESCENT_SPEED;
    public static final ModConfigSpec.DoubleValue MAVIC_MAX_ZOOM;
    public static final ModConfigSpec.DoubleValue MAVIC_HITPOINTS;
    public static final ModConfigSpec.DoubleValue MAVIC_LOOK_SENSITIVITY;

    // Mavic 3T
    public static final ModConfigSpec.IntValue MAVIC_3T_FLIGHT_SECONDS;
    public static final ModConfigSpec.IntValue MAVIC_3T_CONTROL_RANGE;
    public static final ModConfigSpec.DoubleValue MAVIC_3T_TOP_SPEED;

    // FPV flight: P1-SUN, FPV drone and Molniya
    public static final ModConfigSpec.IntValue INTERCEPTOR_FLIGHT_SECONDS;
    public static final ModConfigSpec.IntValue INTERCEPTOR_CONTROL_RANGE;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_CRASH_SPEED;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_LOOK_SENSITIVITY;
    public static final ModConfigSpec.BooleanValue INTERCEPTOR_RATE_MODE;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_ROLL_PITCH_RATE;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_YAW_RATE;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_EXPO;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_MOUSE_GAIN;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_THRUST_TO_WEIGHT;
    public static final ModConfigSpec.IntValue INTERCEPTOR_BATTERY_CELLS;
    public static final ModConfigSpec.IntValue INTERCEPTOR_BATTERY_MAH;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_PEAK_CURRENT;
    public static final ModConfigSpec.ConfigValue<String> INTERCEPTOR_CRAFT_NAME;
    public static final ModConfigSpec.IntValue INTERCEPTOR_OSD_MAX_PITCH;
    public static final ModConfigSpec.IntValue INTERCEPTOR_OSD_MAX_ROLL;

    // P1-SUN
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_TOP_SPEED;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_HITPOINTS;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_BLAST;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_VEHICLE_DAMAGE;
    public static final ModConfigSpec.DoubleValue INTERCEPTOR_VEHICLE_RADIUS;

    // Molniya
    public static final ModConfigSpec.DoubleValue MOLNIYA_HITPOINTS;

    /** Blocks per tick in one km/h: 20 ticks a second, 3.6 km/h per m/s. */
    public static final double KMH_PER_BLOCK_TICK = 72.0D;

    /** Hard cap on route length, whatever the config says. */
    public static final int WAYPOINT_HARD_CAP = 15;
    /** Hard floor on waypoint spacing, whatever the config says. */
    public static final int WAYPOINT_MIN_SPACING_FLOOR = 50;
    /** Lowest flight level a Shahed may be ordered to. */
    public static final int MIN_ALTITUDE = 70;

    static {
        BUILDER.push("targeting");
        BLACKLIST_ENABLED = BUILDER
                .comment("Forbid Shahed targets inside the forbidden zones.")
                .define("blacklistEnabled", false);
        FORBIDDEN_ZONES = BUILDER
                .comment("Forbidden zones, each \"x1,z1,x2,z2\" (two opposite corners).")
                .defineListAllowEmpty("forbiddenZones", List.of(), () -> "0,0,100,100", SkyfrontConfig::isZone);
        WHITELIST_ENABLED = BUILDER
                .comment("Allow Shahed targets only inside the allowed zones.")
                .define("whitelistEnabled", false);
        ALLOWED_ZONES = BUILDER
                .comment("Allowed zones, each \"x1,z1,x2,z2\" (two opposite corners).")
                .defineListAllowEmpty("allowedZones", List.of(), () -> "0,0,100,100", SkyfrontConfig::isZone);
        BUILDER.pop();

        BUILDER.push("explosions");
        EXPLOSION_PARTICLES_ENABLED = BUILDER
                .comment("Shahed explosion particles. Decided by the server for every player.")
                .define("particles", true);
        EXPLOSION_PARTICLE_MULTIPLIER = BUILDER
                .comment("Amount of explosion particles, percent (100 = default).")
                .defineInRange("particleMultiplier", 100, 0, 500);
        EXPLOSION_BREAKS_BLOCKS = BUILDER
                .comment("Whether Shahed, drone warheads and drone ordnance break blocks.")
                .define("breakBlocks", true);
        CUSTOM_EXPLOSION_SOUNDS = BUILDER
                .comment("Shahed explosion sounds, delayed by distance. Decided by the server for every player.")
                .define("customSounds", true);
        BUILDER.pop();

        BUILDER.push("shahed");
        SHAHED_HEALTH = BUILDER
                .comment("Shahed hit points. At zero it is shot down.")
                .defineInRange("health", 40.0, 1.0, 10000.0);
        MAX_FLIGHT_RANGE = BUILDER
                .comment("Farthest a Shahed may fly from its launch point, blocks. Also the reach of the terminal map.")
                .defineInRange("maxFlightRange", 2000, 100, 30000);
        MAX_ROUTE_WAYPOINTS = BUILDER
                .comment("Most waypoints in one route (at most " + WAYPOINT_HARD_CAP + ").")
                .defineInRange("maxRouteWaypoints", 15, 1, WAYPOINT_HARD_CAP);
        MIN_WAYPOINT_DISTANCE = BUILDER
                .comment("Least distance between consecutive waypoints, blocks.")
                .defineInRange("minWaypointDistance", WAYPOINT_MIN_SPACING_FLOOR, WAYPOINT_MIN_SPACING_FLOOR, 1000);
        MIN_LAUNCH_DISTANCE = BUILDER
                .comment("Least distance of any waypoint from the launch point, blocks.")
                .defineInRange("minLaunchDistance", 100, 0, 2000);
        MAX_ALTITUDE = BUILDER
                .comment("Highest cruise altitude (Y) a Shahed may be ordered to (at least " + MIN_ALTITUDE + ").")
                .defineInRange("maxAltitude", 320, MIN_ALTITUDE, 1000);
        BUILDER.pop();

        BUILDER.push("radar");
        RADAR_DETECT_RANGE = BUILDER
                .comment("Radius in which the radar shows drones, blocks.")
                .defineInRange("detectRange", 1000, 50, 30000);
        BUILDER.pop();

        BUILDER.push("siren");
        SIREN_RANGE = BUILDER
                .comment("Distance the air-raid siren can be heard, blocks.")
                .defineInRange("range", 100, 8, 2000);
        BUILDER.pop();

        BUILDER.push("drones");
        DRONE_CHUNK_RADIUS = BUILDER
                .comment("Chunks loaded and streamed around a drone flown beyond its pilot's own view, radius.",
                        "Applies per flying drone: this is the main chunk cost on a busy server.")
                .defineInRange("longRangeChunkRadius", 4, 2, 12);
        DRONE_AIRBURST_CHANCE = BUILDER
                .comment("Chance, percent, that ordnance under a drone goes off in the air when the drone is shot down.")
                .defineInRange("airburstChance", 35, 0, 100);
        DRONE_RAM_DAMAGE = BUILDER
                .comment("Damage a drone deals to what it rams, before any ordnance it carries goes off.")
                .defineInRange("ramDamage", 60.0, 0.0, 100000.0);
        BUILDER.pop();

        BUILDER.comment("Mavic 3 Pro. The Mavic 3T uses these too, except where its own section says otherwise.")
                .push("mavic3Pro");
        MAVIC_FLIGHT_SECONDS = BUILDER
                .comment("Battery life, seconds in the air.")
                .defineInRange("flightSeconds", 300, 10, 36000);
        MAVIC_CONTROL_RANGE = BUILDER
                .comment("Control range, blocks.")
                .defineInRange("controlRange", 500, 16, 4000);
        MAVIC_CRASH_SPEED = BUILDER
                .comment("Impact speed that destroys it, km/h.")
                .defineInRange("crashSpeed", 45.0, 1.0, 1000.0);
        MAVIC_MAX_DESCENT_SPEED = BUILDER
                .comment("Fastest powered descent, km/h. Keep it below crashSpeed.")
                .defineInRange("maxDescentSpeed", 43.0, 1.0, 1000.0);
        MAVIC_MAX_ZOOM = BUILDER
                .comment("Largest camera zoom.")
                .defineInRange("maxZoom", 8.0, 1.0, 64.0);
        MAVIC_HITPOINTS = BUILDER
                .comment("Hit points.")
                .defineInRange("hitpoints", 6.0, 1.0, 10000.0);
        MAVIC_LOOK_SENSITIVITY = BUILDER
                .comment("Gimbal speed against the player's mouse sensitivity.")
                .defineInRange("lookSensitivity", 0.3, 0.05, 2.0);
        BUILDER.pop();

        BUILDER.push("mavic3T");
        MAVIC_3T_FLIGHT_SECONDS = BUILDER
                .comment("Battery life, seconds in the air.")
                .defineInRange("flightSeconds", 480, 10, 36000);
        MAVIC_3T_CONTROL_RANGE = BUILDER
                .comment("Control range, blocks.")
                .defineInRange("controlRange", 1000, 16, 4000);
        MAVIC_3T_TOP_SPEED = BUILDER
                .comment("Top speed, km/h.")
                .defineInRange("topSpeed", 95.0, 1.0, 1000.0);
        BUILDER.pop();

        BUILDER.comment("Flight shared by the P1-SUN, the FPV drone and the Molniya.").push("fpvFlight");
        INTERCEPTOR_FLIGHT_SECONDS = BUILDER
                .comment("Battery life, seconds in the air.")
                .defineInRange("flightSeconds", 180, 10, 36000);
        INTERCEPTOR_CONTROL_RANGE = BUILDER
                .comment("Control range, blocks.")
                .defineInRange("controlRange", 1000, 16, 4000);
        INTERCEPTOR_CRASH_SPEED = BUILDER
                .comment("Impact speed against terrain that destroys it, km/h.")
                .defineInRange("crashSpeed", 70.0, 1.0, 1000.0);
        INTERCEPTOR_LOOK_SENSITIVITY = BUILDER
                .comment("How fast the airframe follows the mouse, against the player's mouse sensitivity.")
                .defineInRange("lookSensitivity", 0.9, 0.05, 2.0);
        INTERCEPTOR_RATE_MODE = BUILDER
                .comment("Rate mode (acro): the sticks set a rate of turn and nothing levels the drone, as on a real FPV quad.",
                        "Off: simplified flight where the drone levels itself and holds height.")
                .define("rateMode", true);
        INTERCEPTOR_ROLL_PITCH_RATE = BUILDER
                .comment("Rate mode: roll and pitch rate at full stick, degrees per second.")
                .defineInRange("rollPitchRate", 420.0, 60.0, 1400.0);
        INTERCEPTOR_YAW_RATE = BUILDER
                .comment("Rate mode: yaw rate at full stick, degrees per second.")
                .defineInRange("yawRate", 300.0, 60.0, 1400.0);
        INTERCEPTOR_EXPO = BUILDER
                .comment("Rate mode: expo, softens the middle of the stick travel.")
                .defineInRange("expo", 0.2, 0.0, 0.9);
        INTERCEPTOR_MOUSE_GAIN = BUILDER
                .comment("Rate mode: how far the virtual stick moves for a given mouse movement.")
                .defineInRange("mouseGain", 1.0, 0.1, 4.0);
        INTERCEPTOR_THRUST_TO_WEIGHT = BUILDER
                .comment("Rate mode: thrust-to-weight ratio at full throttle.")
                .defineInRange("thrustToWeight", 2.1, 1.1, 8.0);
        INTERCEPTOR_BATTERY_CELLS = BUILDER
                .comment("Battery cells shown on the OSD.")
                .defineInRange("batteryCells", 8, 1, 24);
        INTERCEPTOR_BATTERY_MAH = BUILDER
                .comment("Battery capacity, mAh.")
                .defineInRange("batteryMah", 3000, 100, 50000);
        INTERCEPTOR_PEAK_CURRENT = BUILDER
                .comment("Current draw at full load, amps.")
                .defineInRange("peakCurrent", 60.0, 1.0, 500.0);
        INTERCEPTOR_CRAFT_NAME = BUILDER
                .comment("Craft name shown at the top of the OSD.")
                .define("craftName", "P1SUN");
        INTERCEPTOR_OSD_MAX_PITCH = BUILDER
                .comment("Pitch at which the OSD horizon reaches the edge of its travel, degrees.")
                .defineInRange("osdMaxPitch", 20, 5, 90);
        INTERCEPTOR_OSD_MAX_ROLL = BUILDER
                .comment("Roll at which the OSD horizon reaches full tilt, degrees.")
                .defineInRange("osdMaxRoll", 40, 5, 90);
        BUILDER.pop();

        BUILDER.push("p1sun");
        INTERCEPTOR_TOP_SPEED = BUILDER
                .comment("Top speed, km/h.")
                .defineInRange("topSpeed", 210.0, 1.0, 1000.0);
        INTERCEPTOR_HITPOINTS = BUILDER
                .comment("Hit points. Also used by the FPV drone.")
                .defineInRange("hitpoints", 4.0, 1.0, 10000.0);
        INTERCEPTOR_BLAST = BUILDER
                .comment("Warhead explosion power (TNT is 4). Also used by the Molniya.")
                .defineInRange("warheadBlast", 9.0, 0.0, 100.0);
        INTERCEPTOR_VEHICLE_DAMAGE = BUILDER
                .comment("Warhead damage to Superb Warfare vehicles. Also used by the Molniya.")
                .defineInRange("warheadVehicleDamage", 300.0, 0.0, 100000.0);
        INTERCEPTOR_VEHICLE_RADIUS = BUILDER
                .comment("Radius of that vehicle damage, blocks.")
                .defineInRange("warheadVehicleRadius", 10.0, 0.0, 100.0);
        BUILDER.pop();

        BUILDER.push("molniya");
        MOLNIYA_HITPOINTS = BUILDER
                .comment("Hit points.")
                .defineInRange("hitpoints", 30.0, 1.0, 10000.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private static boolean isZone(Object value) {
        return value instanceof String text && ForbiddenZone.parse(text) != null;
    }

    public static int getMaxWaypoints() {
        return Math.min(MAX_ROUTE_WAYPOINTS.get(), WAYPOINT_HARD_CAP);
    }

    public static int getMinWaypointDistance() {
        return Math.max(MIN_WAYPOINT_DISTANCE.get(), WAYPOINT_MIN_SPACING_FLOOR);
    }

    public static int getMaxAltitude() {
        return Math.max(MAX_ALTITUDE.get(), MIN_ALTITUDE);
    }

    public static boolean isInForbiddenZone(int x, int z) {
        return isBlocked(x, z,
                WHITELIST_ENABLED.get(), packZones(ALLOWED_ZONES.get()),
                BLACKLIST_ENABLED.get(), packZones(FORBIDDEN_ZONES.get()));
    }

    /**
     * The zone rule, free of config lookups so the client can run the same check on zone data sent from the
     * server; returns true when the position may not be targeted.
     */
    public static boolean isBlocked(int x, int z, boolean whitelistEnabled, int[] whitelist,
                                    boolean blacklistEnabled, int[] blacklist) {
        if (whitelistEnabled && !isInsideAny(x, z, whitelist)) {
            return true;
        }
        return blacklistEnabled && isInsideAny(x, z, blacklist);
    }

    private static boolean isInsideAny(int x, int z, int[] zones) {
        for (int i = 0; i + 3 < zones.length; i += 4) {
            int minX = Math.min(zones[i], zones[i + 2]);
            int maxX = Math.max(zones[i], zones[i + 2]);
            int minZ = Math.min(zones[i + 1], zones[i + 3]);
            int maxZ = Math.max(zones[i + 1], zones[i + 3]);
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                return true;
            }
        }
        return false;
    }

    /** Parses "x1,z1,x2,z2" zone strings into flattened quads; bad entries are skipped. */
    public static int[] packZones(List<? extends String> zones) {
        List<Integer> out = new ArrayList<>();
        for (String text : zones) {
            ForbiddenZone zone = ForbiddenZone.parse(text);
            if (zone != null) {
                out.add(zone.x1);
                out.add(zone.z1);
                out.add(zone.x2);
                out.add(zone.z2);
            }
        }
        int[] packed = new int[out.size()];
        for (int i = 0; i < out.size(); i++) {
            packed[i] = out.get(i);
        }
        return packed;
    }

    /** Changes a setting and saves the file at once. */
    private static <T> void store(ModConfigSpec.ConfigValue<T> value, T newValue) {
        value.set(newValue);
        SPEC.save();
    }

    /** The result of {@link #reloadFromFile}: settings changed, settings refused, whether the file was there. */
    public record ReloadResult(int changed, List<String> rejected, boolean fileFound) {
    }

    /** Re-reads the config file and takes every valid setting in it; invalid ones keep their value. */
    public static ReloadResult reloadFromFile() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        if (!Files.exists(path)) {
            return new ReloadResult(0, List.of(), false);
        }
        int[] changed = {0};
        List<String> rejected = new ArrayList<>();
        try (CommentedFileConfig file = CommentedFileConfig.builder(path).sync().build()) {
            file.load();
            reloadInto(SPEC.getValues(), file, changed, rejected);
        }
        return new ReloadResult(changed[0], rejected, true);
    }

    @SuppressWarnings("unchecked")
    private static void reloadInto(UnmodifiableConfig values, CommentedFileConfig file, int[] changed,
                                   List<String> rejected) {
        for (Object node : values.valueMap().values()) {
            if (node instanceof UnmodifiableConfig section) {
                reloadInto(section, file, changed, rejected);
                continue;
            }
            if (!(node instanceof ModConfigSpec.ConfigValue<?> setting)) {
                continue;
            }
            Object raw = file.get(setting.getPath());
            if (raw == null) {
                continue;
            }
            Object current = setting.get();
            Object value = raw;
            if (raw instanceof Number number) {
                if (current instanceof Integer) {
                    value = number.intValue();
                } else if (current instanceof Long) {
                    value = number.longValue();
                } else if (current instanceof Double) {
                    value = number.doubleValue();
                }
            }
            Object spec = SPEC.getSpec().get(setting.getPath());
            if (spec instanceof ModConfigSpec.ValueSpec valueSpec && !valueSpec.test(value)) {
                rejected.add(String.join(".", setting.getPath()));
                continue;
            }
            if (!value.equals(current)) {
                ((ModConfigSpec.ConfigValue<Object>) setting).set(value);
                changed[0]++;
            }
        }
    }

    public static boolean areExplosionParticlesEnabled() {
        return EXPLOSION_PARTICLES_ENABLED.get();
    }

    public static void setExplosionParticlesEnabled(boolean enabled) {
        store(EXPLOSION_PARTICLES_ENABLED, enabled);
    }

    public static int getParticleMultiplier() {
        return EXPLOSION_PARTICLE_MULTIPLIER.get();
    }

    public static void setParticleMultiplier(int multiplier) {
        store(EXPLOSION_PARTICLE_MULTIPLIER, Math.max(0, Math.min(500, multiplier)));
    }

    public static boolean doExplosionsBreakBlocks() {
        return EXPLOSION_BREAKS_BLOCKS.get();
    }

    public static void setExplosionsBreakBlocks(boolean breaks) {
        store(EXPLOSION_BREAKS_BLOCKS, breaks);
    }

    public static boolean areCustomExplosionSoundsEnabled() {
        return CUSTOM_EXPLOSION_SOUNDS.get();
    }

    public static void setCustomExplosionSoundsEnabled(boolean enabled) {
        store(CUSTOM_EXPLOSION_SOUNDS, enabled);
    }

    public static boolean isBlacklistEnabled() {
        return BLACKLIST_ENABLED.get();
    }

    public static void setBlacklistEnabled(boolean enabled) {
        store(BLACKLIST_ENABLED, enabled);
    }

    public static boolean isWhitelistEnabled() {
        return WHITELIST_ENABLED.get();
    }

    public static void setWhitelistEnabled(boolean enabled) {
        store(WHITELIST_ENABLED, enabled);
    }

    /** A rectangular zone, normalised so that (x1, z1) is the low corner. */
    public static class ForbiddenZone {
        public final int x1;
        public final int z1;
        public final int x2;
        public final int z2;

        public ForbiddenZone(int x1, int z1, int x2, int z2) {
            this.x1 = Math.min(x1, x2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2);
            this.z2 = Math.max(z1, z2);
        }

        public boolean contains(int x, int z) {
            return x >= this.x1 && x <= this.x2 && z >= this.z1 && z <= this.z2;
        }

        /** Parses "x1,z1,x2,z2", or returns null if the text is not a zone. */
        public static ForbiddenZone parse(String text) {
            try {
                String[] parts = text.split(",");
                if (parts.length == 4) {
                    return new ForbiddenZone(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()),
                            Integer.parseInt(parts[3].trim()));
                }
            } catch (NumberFormatException ignored) {
            }
            return null;
        }
    }
}
