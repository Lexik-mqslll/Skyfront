package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Everything the OSD displays, read off the drone and its operator in one go. */
public record DroneTelemetry(boolean moving, float charge, float droneYaw,
                             double verticalKmh, double horizontalKmh,
                             double altitude, double distance, double operatorYaw,
                             int satellites, int linkBars, double linkRange,
                             int remainingSeconds, int flightSeconds,
                             String resolution) {
    public static DroneTelemetry read(Minecraft minecraft, DroneEntity drone, LocalPlayer player) {
        Vec3 velocity = drone.getDeltaMovement();

        double dx = player.getX() - drone.getX();
        double dz = player.getZ() - drone.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        int satellites = drone.level().getBrightness(LightLayer.SKY, drone.blockPosition()) * 2 + 4;

        double linkRange = DroneControl.controlRange();
        double range = player.distanceTo(drone);
        int bars = Mth.clamp((int) Math.ceil((1.0D - range / linkRange) * 5.0D), 0, 5);

        return new DroneTelemetry(
                velocity.length() > 0.03D,
                drone.getCharge(),
                drone.getYRot(),
                velocity.y * 72.0D,
                velocity.horizontalDistance() * 72.0D,
                drone.getY() - player.getY(),
                horizontal,
                Math.toDegrees(Math.atan2(-dx, dz)),
                satellites,
                bars,
                linkRange,
                (int) (drone.getCharge() * drone.fullFlightTicks() / 20.0F),
                DroneControl.flightSeconds(),
                minecraft.getWindow().getHeight() + "p" + Math.min(minecraft.getFps(), 999));
    }

    /** Seconds of flight the remaining charge is good for. */
    public String remainingTime() {
        return String.format(Locale.ROOT, "%d'%02d\"", this.remainingSeconds / 60, this.remainingSeconds % 60);
    }
}
