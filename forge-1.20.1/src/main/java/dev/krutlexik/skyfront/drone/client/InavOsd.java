package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.FpvPack;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** The Molniya's OSD: a fixed-wing flight controller's screen, laid out the way INAV lays it out. */
public class InavOsd extends AnalogOsd {
    private static final int SYM_SIGNAL = 0x01;
    private static final int SYM_VOLTS = 0x06;
    private static final int SYM_MAH = 0x07;
    private static final int SYM_METRE = 0x0C;
    private static final int SYM_SATELLITE = 0x1F;
    private static final int SYM_POINT_RIGHT = 0x77;
    private static final int SYM_POINT_LEFT = 0x78;
    private static final int SYM_BATTERY_FULL = 0x90;
    private static final int SYM_BATTERY_EMPTY = 0x96;
    private static final int SYM_MAIN_BATTERY = 0x97;
    private static final int SYM_AMPS = 0x9A;
    private static final int SYM_KMH = 0x9E;
    private static final int SYM_MS = 0x9F;

    private static final int LEFT_COL = 2;
    private static final int RIGHT_COL = 28;
    private static final int ROW_TOP = 1;
    private static final int ROW_SECOND = 2;
    private static final int ROW_THIRD = 3;
    /** The airframe's pitch readout: under the cell voltage, clear of the heading tape and the speed ladder. */
    private static final int ROW_PITCH = 4;
    /** The row the aircraft symbol and the two ladder readouts sit on. */
    private static final int ROW_CENTRE = 7;
    private static final int ROW_WARNING = 11;
    private static final int ROW_BOTTOM_1 = 12;
    private static final int ROW_BOTTOM_2 = 13;
    private static final int ROW_BOTTOM_3 = 14;

    /** Heading tape: its half-width in font pixels, and how many degrees each font pixel is worth. */
    private static final int TAPE_HALF = 78;
    private static final float TAPE_PX_PER_DEGREE = 0.8F;
    /** The ladders: half their height in font pixels, and where they stand. */
    private static final int LADDER_HALF = 44;
    private static final float SPEED_LADDER_COL = 7.2F;
    private static final float ALT_LADDER_COL = 23.2F;
    /** Font pixels between ladder ticks, and what each tick is worth. */
    private static final int LADDER_TICK_PX = 6;
    private static final float SPEED_PER_TICK = 5.0F;
    private static final float ALT_PER_TICK = 5.0F;

    private static final long BLINK_MS = 400L;
    /** The flight mode on the screen: this airframe is flown fly-by-wire, angle-stabilised. */
    private static final String FLIGHT_MODE = "FBWA";

    /** Everything the screen shows, gathered once so a frozen picture can show it again unchanged. */
    private record Reading(double tripKm, double cellVolts, float heading, int pitch, int roll,
                           double speedKmh, double altitude, double homeDistance, double climb,
                           double packVolts, double amps, int mah, float charge, int seconds,
                           boolean armed, boolean armRefused, boolean armedFlash, float pitchExact) {
    }

    private static int tripDroneId = -1;
    private static double tripBlocks;
    private static Vec3 tripLast;

    /** The last reading before the link went, held on the frozen picture. */
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
        if (lost == null) {
            return;
        }
        setUpFrame(graphics);
        draw(lost, true);
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

        Vec3 velocity = drone.getDeltaMovement();
        float heading = Mth.positiveModulo(drone.getViewYRot(partialTick) + 180.0F, 360.0F);
        return new Reading(
                tripBlocks / 1000.0D,
                FpvBattery.cellVolts(),
                heading,
                Math.round(drone.bodyPitch(partialTick)),
                Math.round(drone.bodyRoll(partialTick)),
                velocity.length() * 72.0D,
                altitude(drone),
                homeDistance(drone),
                velocity.y * 20.0D,
                FpvBattery.packVolts(),
                FpvBattery.amps(),
                FpvBattery.mahDrawn(drone),
                drone.getCharge(),
                FpvBattery.armedSeconds(),
                drone.isArmed(),
                DroneControl.isArmRefused(),
                DroneControl.isArmedFlash(),
                drone.bodyPitch(partialTick));
    }

    private void draw(Reading r, boolean linkLost) {
        boolean blink = (System.currentTimeMillis() / BLINK_MS) % 2 == 0;

        text(LEFT_COL, ROW_TOP, String.format(Locale.ROOT, "%.1fKM", r.tripKm()));
        text(LEFT_COL, ROW_PITCH, String.format(Locale.ROOT, "PIT %+.1f", r.pitchExact()));

        drawHeadingTape(r.heading());
        centre(ROW_THIRD, String.format(Locale.ROOT, "%03d", Math.round(r.heading()) % 360));

        if (!linkLost) {
            rightText(RIGHT_COL, ROW_TOP, glyph(SYM_SATELLITE) + glyph(SYM_SIGNAL));
        }
        rightText(RIGHT_COL, ROW_SECOND, String.format(Locale.ROOT, "%+d P", r.pitch()));
        rightText(RIGHT_COL, ROW_THIRD, String.format(Locale.ROOT, "%+d R", r.roll()));

        drawLadder(SPEED_LADDER_COL, (float) (r.speedKmh() / SPEED_PER_TICK), true);
        rightText((int) SPEED_LADDER_COL, ROW_CENTRE,
                String.format(Locale.ROOT, "%03d", Math.round(r.speedKmh())) + glyph(SYM_POINT_RIGHT));
        rightText((int) SPEED_LADDER_COL - 1, ROW_CENTRE + 1, glyph(SYM_KMH));

        drawLadder(ALT_LADDER_COL, (float) (r.altitude() / ALT_PER_TICK), false);
        text((int) ALT_LADDER_COL + 1, ROW_CENTRE,
                glyph(SYM_POINT_LEFT) + String.format(Locale.ROOT, "%04d", Math.round(r.altitude())));
        text((int) ALT_LADDER_COL + 2, ROW_CENTRE + 1, glyph(SYM_METRE));

        drawAircraft();

        boolean packWarning = r.armed() && r.cellVolts() < FpvPack.CELL_LOW;
        if (!packWarning || blink || linkLost) {
            text(LEFT_COL - 1, ROW_BOTTOM_1, glyph(SYM_MAIN_BATTERY)
                    + String.format(Locale.ROOT, "%.2f", r.cellVolts()) + glyph(SYM_VOLTS));
        }
        text(LEFT_COL, ROW_BOTTOM_2, String.format(Locale.ROOT, "%.1f", r.amps()) + glyph(SYM_AMPS));
        text(LEFT_COL - 1, ROW_BOTTOM_3, glyph(batterySymbol(r.charge())) + r.mah() + glyph(SYM_MAH));

        if (linkLost) {
            offsetFromCentre(ROW_WARNING, "RX LOSS", -4.0F);
            centre(ROW_BOTTOM_1, FLIGHT_MODE);
        } else if (!r.armed()) {
            centre(ROW_BOTTOM_1, r.armRefused() ? "THROTTLE" : "DISARMED");
        } else if (r.armedFlash()) {
            centre(ROW_BOTTOM_1, "ARMED");
        } else {
            centre(ROW_BOTTOM_1, FLIGHT_MODE);
            if (r.cellVolts() < FpvPack.CELL_LAND && blink) {
                centre(ROW_WARNING, "LAND NOW");
            } else if (packWarning && blink) {
                centre(ROW_WARNING, "LOW BATTERY");
            }
        }
        centre(ROW_BOTTOM_3, Math.round(r.homeDistance()) + glyph(SYM_METRE));

        rightText(RIGHT_COL, ROW_BOTTOM_1,
                String.format(Locale.ROOT, "%+d", Math.round(r.climb())) + glyph(SYM_MS));
        rightText(RIGHT_COL, ROW_BOTTOM_2,
                "U" + String.format(Locale.ROOT, "%.1f", r.packVolts()) + glyph(SYM_VOLTS));
        rightText(RIGHT_COL, ROW_BOTTOM_3,
                String.format(Locale.ROOT, "%d:%02d", r.seconds() / 60, r.seconds() % 60));
    }

    /**
     * The heading tape: a tick every five degrees, a long one every fifteen, the cardinal letters standing
     * over their own points, and a fixed pointer above the middle marking the nose.
     */
    private void drawHeadingTape(float heading) {
        float centreX = colX(CENTRE_COL + 0.5F);
        float tapeY = rowY(ROW_SECOND + 0.75F);
        java.util.List<int[]> lines = new java.util.ArrayList<>();
        int first = (int) Math.ceil((heading - TAPE_HALF / TAPE_PX_PER_DEGREE) / 5.0F) * 5;
        for (int degrees = first; degrees <= heading + TAPE_HALF / TAPE_PX_PER_DEGREE; degrees += 5) {
            int x = Math.round((degrees - heading) * TAPE_PX_PER_DEGREE);
            int length = Math.floorMod(degrees, 15) == 0 ? 4 : 2;
            lines.add(new int[] {x, 0, x, -length});
        }
        lines.add(new int[] {-2, -12, 2, -12});
        lines.add(new int[] {-1, -11, 1, -11});
        lines.add(new int[] {0, -10, 0, -10});
        strokes(centreX, tapeY, lines.toArray(new int[0][]));

        String[] names = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            float offset = Mth.wrapDegrees(i * 90.0F - heading);
            if (Math.abs(offset) * TAPE_PX_PER_DEGREE > TAPE_HALF - GLYPH_W / 2.0F) {
                continue;
            }
            float x = centreX + offset * TAPE_PX_PER_DEGREE * this.glyphW / GLYPH_W - this.glyphW / 2.0F;
            drawString(x, rowY(ROW_SECOND) - this.glyphH * 0.55F, names[i]);
        }
    }

    /**
     * A ladder: a vertical rule with ticks running past it as the value changes — one tick per step, a long
     * one every other — pointing in toward the middle of the picture.
     */
    private void drawLadder(float col, float ticks, boolean left) {
        float x = colX(col);
        float y = rowY(ROW_CENTRE + 0.5F);
        java.util.List<int[]> lines = new java.util.ArrayList<>();
        lines.add(new int[] {0, -LADDER_HALF, 0, LADDER_HALF});
        int whole = (int) Math.floor(ticks);
        float fraction = ticks - whole;
        int reach = LADDER_HALF / LADDER_TICK_PX;
        for (int i = -reach - 1; i <= reach + 1; i++) {
            int ty = Math.round((i + fraction) * LADDER_TICK_PX);
            if (Math.abs(ty) > LADDER_HALF) {
                continue;
            }
            int length = Math.floorMod(whole - i, 2) == 0 ? 4 : 2;
            lines.add(left ? new int[] {0, ty, length, ty} : new int[] {-length, ty, 0, ty});
        }
        strokes(x, y, lines.toArray(new int[0][]));
    }

    /** The aircraft symbol: a small ring where the nose points, with a short upright stroke above it. */
    private void drawAircraft() {
        strokes(colX(CENTRE_COL + 0.5F), rowY(ROW_CENTRE + 0.5F), AIRCRAFT);
    }

    /** The ring (radius four font pixels) and the stroke over it, as single-pixel runs. */
    private static final int[][] AIRCRAFT = {
            {-1, -4, 1, -4}, {-1, 4, 1, 4}, {-4, -1, -4, 1}, {4, -1, 4, 1},
            {-3, -3, -2, -3}, {2, -3, 3, -3}, {-3, 3, -2, 3}, {2, 3, 3, 3},
            {-3, -2, -3, -2}, {3, -2, 3, -2}, {-3, 2, -3, 2}, {3, 2, 3, 2},
            {0, -14, 0, -8},
    };

    /** The battery glyph empties as the pack does. */
    private static int batterySymbol(float charge) {
        int step = Math.round((1.0F - Mth.clamp(charge, 0.0F, 1.0F)) * 6.0F);
        return Mth.clamp(SYM_BATTERY_FULL + step, SYM_BATTERY_FULL, SYM_BATTERY_EMPTY);
    }
}
