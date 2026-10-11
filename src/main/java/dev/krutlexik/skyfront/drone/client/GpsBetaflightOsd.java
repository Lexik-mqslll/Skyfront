package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.FpvPack;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** The FPV drone's OSD: a Betaflight layout with GPS readouts. */
public class GpsBetaflightOsd extends AnalogOsd {
    private static final int SYM_RSSI = 0x01;
    private static final int SYM_THROTTLE = 0x04;
    private static final int SYM_VOLTS = 0x06;
    private static final int SYM_MAH = 0x07;
    private static final int SYM_METRE = 0x0C;
    private static final int SYM_HOME = 0x11;
    private static final int SYM_SAT_LEFT = 0x1E;
    private static final int SYM_SAT_RIGHT = 0x1F;
    /** Sixteen arrows, pointing down at 0x60 and turning clockwise round to it again. */
    private static final int SYM_ARROW_SOUTH = 0x60;
    /** Betaflight's crosshair: a left wing, the centre mark and a right wing. */
    private static final int SYM_AH_CENTER_LINE = 0x72;
    private static final int SYM_AH_CENTER = 0x73;
    private static final int SYM_AH_CENTER_LINE_RIGHT = 0x74;
    /** Painted into cells the font left empty: a speedometer, and a turning arrow for distance flown. */
    private static final int SYM_SPEED = 0x7B;
    private static final int SYM_TRIP = 0x7D;
    private static final int SYM_ALTITUDE = 0x7F;
    private static final int SYM_LAT = 0x89;
    private static final int SYM_BATTERY_FULL = 0x90;
    private static final int SYM_BATTERY_EMPTY = 0x96;
    private static final int SYM_LON = 0x98;
    private static final int SYM_AMPS = 0x9A;
    private static final int SYM_FLY_TIME = 0x9C;
    private static final int SYM_KMH = 0x9E;

    private static final int LEFT_COL = 0;
    private static final int RIGHT_COL = COLS;
    private static final int ROW_1 = 0;
    private static final int ROW_2 = 1;
    private static final int ROW_3 = 2;
    private static final int ROW_4 = 3;
    private static final int ROW_5 = 4;
    private static final int ROW_HORIZON = 8;
    private static final int ROW_HOME = 11;
    private static final int ROW_LOSS = 12;
    private static final int ROW_LAT = 14;
    private static final int ROW_LON = 15;
    /** The warning sits right of centre, clear of the longitude to its left and the link to its right. */
    private static final float WARNING_OFFSET = 2.0F;

    /** The link mode readout: rate profile, packet rate, power. */
    private static final String LINK_MODE = "R:2:200:P";
    /** The radio link's own rate, the fixed half of the link-quality figure. */
    private static final int LQ_MODE = 2;
    private static final int RSSI_BEST = -30;
    private static final int RSSI_WORST = -130;
    private static final int RSSI_WARN = -100;
    private static final int LQ_WARN = 25;

    /** Metres, here blocks, to a degree of latitude. */
    private static final double METRES_PER_DEGREE = 111_320.0D;

    private static final long BLINK_MS = 400L;

    /** Everything the screen shows, gathered once so a frozen picture can show it again unchanged. */
    private record Reading(int seconds, double cellVolts, float charge, int mah, double amps, int throttle,
                           double altitude, double tripBlocks, double speedKmh, int satellites,
                           float pitch, float roll, int homeArrow, double homeDistance,
                           double latitude, double longitude, int rssi, int linkQuality,
                           boolean armed, boolean armRefused, boolean armedFlash) {
    }

    private static int tripDroneId = -1;
    private static double tripBlocks;
    private static Vec3 tripLast;

    /** The last reading before the link went, held under the static. */
    private static Reading lost;

    /** Called with the drone still in hand, right before the feed dies. */
    public static void captureSignalLoss(DroneEntity drone) {
        lost = read(drone, 1.0F);
    }

    public void render(GuiGraphics graphics, DroneEntity drone, float partialTick) {
        setUpFrame(graphics);
        draw(read(drone, partialTick), false);
    }

    public void renderSignalLost(GuiGraphics graphics) {
        setUpFrame(graphics);
        if (lost != null) {
            draw(lost, true);
        }
    }

    private static Reading read(DroneEntity drone, float partialTick) {
        if (drone.getId() != tripDroneId) {
            tripDroneId = drone.getId();
            tripBlocks = 0.0D;
            tripLast = null;
        }
        Vec3 here = drone.position();
        if (tripLast != null) {
            double step = here.distanceTo(tripLast);
            if (step < 20.0D) {
                tripBlocks += step;
            }
        }
        tripLast = here;

        float heading = Mth.positiveModulo(drone.getViewYRot(partialTick) + 180.0F, 360.0F);
        int toHome = Math.floorMod(Math.round((float) (homeBearing(drone) - heading) / 22.5F), 16);
        int arrow = SYM_ARROW_SOUTH + (16 - toHome + 8) % 16;

        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        double reach = minecraft.player == null ? 0.0D
                : Mth.clamp(minecraft.player.distanceTo(drone) / DroneControl.controlRange(), 0.0D, 1.0D);

        long wander = System.currentTimeMillis() / 20_000L + drone.getId();
        int satellites = 12 + Math.floorMod(drone.getId(), 3) + (int) Math.floorMod(wander, 3L);

        return new Reading(
                FpvBattery.armedSeconds(),
                FpvBattery.cellVolts(),
                drone.getCharge(),
                FpvBattery.mahDrawn(drone),
                FpvBattery.amps(),
                Math.round(drone.throttleStick() * 100.0F),
                altitude(drone),
                tripBlocks,
                drone.getDeltaMovement().length() * 72.0D,
                satellites,
                drone.bodyPitch(partialTick),
                drone.bodyRoll(partialTick),
                arrow,
                homeDistance(drone),
                -drone.getZ() / METRES_PER_DEGREE,
                drone.getX() / METRES_PER_DEGREE,
                (int) Math.round(RSSI_BEST - (double) (RSSI_BEST - RSSI_WORST) * reach),
                (int) Math.round(100.0D * (1.0D - reach)),
                drone.isArmed(),
                DroneControl.isArmRefused(),
                DroneControl.isArmedFlash());
    }

    private void draw(Reading r, boolean linkLost) {
        boolean blink = (System.currentTimeMillis() / BLINK_MS) % 2 == 0;
        boolean packWarning = r.armed() && r.cellVolts() < FpvPack.CELL_LOW;

        text(LEFT_COL, ROW_1, glyph(SYM_FLY_TIME)
                + String.format(Locale.ROOT, "%02d:%02d", r.seconds() / 60, r.seconds() % 60));
        centre(ROW_1, LINK_MODE);
        rightText(RIGHT_COL, ROW_1, glyph(SYM_ALTITUDE)
                + String.format(Locale.ROOT, "%.1f", r.altitude()) + glyph(SYM_METRE));

        if (!packWarning || blink || linkLost) {
            text(LEFT_COL, ROW_2, glyph(batterySymbol(r.charge()))
                    + String.format(Locale.ROOT, "%.2f", r.cellVolts()) + glyph(SYM_VOLTS));
        }
        text(LEFT_COL, ROW_3, r.mah() + glyph(SYM_MAH));
        text(LEFT_COL, ROW_4, String.format(Locale.ROOT, "%.2f", r.amps()) + glyph(SYM_AMPS));
        text(LEFT_COL, ROW_5, glyph(SYM_THROTTLE) + String.format(Locale.ROOT, "%3d", r.throttle()));

        rightText(RIGHT_COL, ROW_2, glyph(SYM_TRIP) + Math.round(r.tripBlocks()) + glyph(SYM_METRE));
        rightText(RIGHT_COL, ROW_3, glyph(SYM_SPEED)
                + String.format(Locale.ROOT, "%3d", Math.round(r.speedKmh())) + glyph(SYM_KMH));
        rightText(RIGHT_COL, ROW_4, glyph(SYM_SAT_LEFT) + glyph(SYM_SAT_RIGHT) + r.satellites());

        drawHorizon(ROW_HORIZON, r.pitch(), r.roll());
        blitCell(CENTRE_COL - 1, ROW_HORIZON, SYM_AH_CENTER_LINE);
        blitCell(CENTRE_COL, ROW_HORIZON, SYM_AH_CENTER);
        blitCell(CENTRE_COL + 1, ROW_HORIZON, SYM_AH_CENTER_LINE_RIGHT);
        centre(ROW_HOME, glyph(r.homeArrow()) + " " + glyph(SYM_HOME)
                + Math.round(r.homeDistance()) + glyph(SYM_METRE));

        text(LEFT_COL, ROW_LAT, glyph(SYM_LAT) + coordinate(r.latitude(), 2));
        text(LEFT_COL, ROW_LON, glyph(SYM_LON) + coordinate(r.longitude(), 3));

        String warning = null;
        boolean steady = true;
        if (linkLost) {
            offsetFromCentre(ROW_LOSS, "RX LOSS", -4.0F);
        } else if (!r.armed()) {
            warning = r.armRefused() ? "THROTTLE" : "DISARMED";
        } else if (r.armedFlash()) {
            warning = "ARMED";
        } else if (r.cellVolts() < FpvPack.CELL_LAND) {
            warning = "LAND NOW";
            steady = false;
        } else if (packWarning) {
            warning = "LOW VOLTAGE";
            steady = false;
        }
        if (warning != null && (steady || blink)) {
            offsetFromCentre(ROW_LON, warning, WARNING_OFFSET);
        }

        int rssi = linkLost ? RSSI_WORST : r.rssi();
        int quality = linkLost ? 0 : r.linkQuality();
        if (rssi > RSSI_WARN || blink || linkLost) {
            rightText(RIGHT_COL, ROW_LAT, glyph(SYM_RSSI) + rssi);
        }
        if (quality > LQ_WARN || blink || linkLost) {
            rightText(RIGHT_COL, ROW_LON, "LQ" + LQ_MODE + ":" + quality);
        }
    }

    /** Degrees to four places, with as many whole digits as the axis needs: -00.0000, -000.0000. */
    private static String coordinate(double degrees, int wholeDigits) {
        String sign = degrees < 0.0D ? "-" : "";
        return sign + String.format(Locale.ROOT, "%0" + (wholeDigits + 5) + ".4f", Math.abs(degrees));
    }

    /** The battery glyph empties as the pack does. */
    private static int batterySymbol(float charge) {
        int step = Math.round((1.0F - Mth.clamp(charge, 0.0F, 1.0F)) * 6.0F);
        return Mth.clamp(SYM_BATTERY_FULL + step, SYM_BATTERY_FULL, SYM_BATTERY_EMPTY);
    }
}
