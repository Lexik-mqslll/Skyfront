package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.drone.FpvPack;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** The interceptor's OSD: a flight controller's own screen, in Betaflight's own font. */
public class BetaflightOsd extends AnalogOsd implements LayeredDraw.Layer {
    private static final int SYM_RSSI = 0x01;
    private static final int SYM_VOLTS = 0x06;
    private static final int SYM_MAH = 0x07;
    private static final int SYM_METRE = 0x0C;
    private static final int SYM_ALTITUDE = 0x7F;
    /** Seven steps, full through to empty. */
    private static final int SYM_BATTERY_FULL = 0x90;
    private static final int SYM_BATTERY_EMPTY = 0x96;
    private static final int SYM_AMPS = 0x9A;
    private static final int SYM_FLY_TIME = 0x9C;
    private static final int SYM_MS = 0x9F;
    private static final int SYM_ARROW_UP = 0x75;
    private static final int SYM_ARROW_DOWN = 0x76;

    /** OSD rows. */
    private static final int ROW_MODE = 1;
    private static final int ROW_RATE = 2;
    private static final int ROW_HORIZON = 8;
    private static final int ROW_ALT = 9;
    private static final int ROW_AMPS = 12;
    private static final int ROW_VOLTS = 13;
    private static final int ROW_MAH = 14;
    private static final int ROW_FUSE = 13;
    private static final int ROW_TIMER = 14;

    /**
     * The packet rate the link is running, which is the fixed half of the link-quality readout: the figure
     * beside it is the quality itself, out of a hundred.
     */
    private static final int LQ_RATE = 20;

    /** The ends of the link's own scale, reached at the operator and at the very edge of control range. */
    private static final int LQ_BEST = 100;
    private static final int RSSI_BEST = -30;
    private static final int RSSI_WORST = -110;

    /** Where each starts flashing: the link is going, and the pilot has to be told before it goes. */
    private static final int LQ_WARN = 25;
    private static final int RSSI_WARN = -80;

    /** How long after arming the fuse warning starts flashing, and how fast it flashes. */
    private static final int FUSE_ARM_SECONDS = 10;
    private static final long FUSE_BLINK_MS = 400L;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        DroneFeedFreeze.captureIfRequested();

        if (DroneControl.isPictureHeld()) {
            if (lossAnalog) {
                if (lossStyle == DroneEntity.Osd.INAV) {
                    INAV.renderSignalLost(graphics);
                } else if (lossStyle == DroneEntity.Osd.BETAFLIGHT_GPS) {
                    GPS.renderSignalLost(graphics);
                } else {
                    setUpFrame(graphics);
                }
                dev.krutlexik.skyfront.drone.client.render.AnalogFeedPass.renderStatic(graphics);
                return;
            }
            DroneFeedFreeze.renderHold(graphics);
            if (lossStyle == DroneEntity.Osd.BETAFLIGHT) {
                setUpFrame(graphics);
                renderSignalLost();
            } else if (lossStyle == DroneEntity.Osd.INAV) {
                INAV.renderSignalLost(graphics);
            } else if (lossStyle == DroneEntity.Osd.BETAFLIGHT_GPS) {
                GPS.renderSignalLost(graphics);
            }
            return;
        }

        DroneEntity drone = DroneControl.drone();
        if (drone instanceof dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity analog
                && analog.analogVideo()) {
            dev.krutlexik.skyfront.drone.client.render.AnalogFeedPass.renderPicture(graphics, analog);
        }
        if (drone != null && drone.osdStyle() == DroneEntity.Osd.INAV) {
            INAV.render(graphics, drone, deltaTracker.getGameTimeDeltaPartialTick(false));
            if (drone instanceof dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity analog
                    && analog.analogVideo()) {
                dev.krutlexik.skyfront.drone.client.render.AnalogFeedPass.renderOverlay(graphics);
            }
            return;
        }
        if (drone != null && drone.osdStyle() == DroneEntity.Osd.BETAFLIGHT_GPS) {
            GPS.render(graphics, drone, deltaTracker.getGameTimeDeltaPartialTick(false));
            if (drone instanceof dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity analog
                    && analog.analogVideo()) {
                dev.krutlexik.skyfront.drone.client.render.AnalogFeedPass.renderOverlay(graphics);
            }
            return;
        }
        if (drone != null && drone.osdStyle() == DroneEntity.Osd.NONE) {
            setUpFrame(graphics);
            if (drone instanceof dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity analog
                    && analog.analogVideo()) {
                dev.krutlexik.skyfront.drone.client.render.AnalogFeedPass.renderOverlay(graphics);
            }
            return;
        }
        if (drone == null || drone.osdStyle() != DroneEntity.Osd.BETAFLIGHT) {
            return;
        }

        setUpFrame(graphics);
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

        Vec3 velocity = drone.getDeltaMovement();
        double altitude = altitude(drone);
        double reach = Mth.clamp(minecraft.player.distanceTo(drone) / DroneControl.controlRange(), 0.0D, 1.0D);
        int linkQuality = (int) Math.round(LQ_BEST * (1.0D - reach));
        int rssiDbm = (int) Math.round(RSSI_BEST - (double) (RSSI_BEST - RSSI_WORST) * reach);
        int antennas = 2;
        double climb = velocity.y * 20.0D;
        boolean blink = (System.currentTimeMillis() / FUSE_BLINK_MS) % 2 == 0;

        text(EDGE_COL, ROW_MODE, "ACRO");
        text(EDGE_COL, ROW_RATE, "R:1:2.5");
        centre(ROW_MODE, SkyfrontConfig.INTERCEPTOR_CRAFT_NAME.get().toUpperCase(Locale.ROOT));
        if (rssiDbm > RSSI_WARN || blink) {
            rightText(COLS - EDGE_COL, ROW_MODE, glyph(SYM_RSSI) + rssiDbm + ":" + antennas);
        }
        if (linkQuality > LQ_WARN || blink) {
            rightText(COLS - EDGE_COL, ROW_RATE, "LQ" + LQ_RATE + ":" + linkQuality);
        }

        rightText(COLS - EDGE_COL, ROW_HORIZON,
                glyph(climb < 0.0D ? SYM_ARROW_DOWN : SYM_ARROW_UP)
                        + String.format(Locale.ROOT, "%.1f", Math.abs(climb)) + glyph(SYM_MS));
        rightText(COLS - EDGE_COL, ROW_ALT,
                glyph(SYM_ALTITUDE) + String.format(Locale.ROOT, "%.1f", altitude) + glyph(SYM_METRE));

        drawHorizon(ROW_HORIZON, drone.bodyPitch(partialTick), drone.bodyRoll(partialTick));
        drawCrosshair();

        double cell = FpvBattery.cellVolts();
        boolean packWarning = drone.isArmed() && cell < FpvPack.CELL_LOW;

        text(EDGE_COL, ROW_AMPS, String.format(Locale.ROOT, "%.2f", FpvBattery.amps()) + glyph(SYM_AMPS));
        if (!packWarning || blink) {
            text(EDGE_COL, ROW_VOLTS, glyph(batterySymbol(drone.getCharge()))
                    + String.format(Locale.ROOT, "%.2f", cell) + glyph(SYM_VOLTS));
        }
        text(EDGE_COL, ROW_MAH, FpvBattery.mahDrawn(drone) + glyph(SYM_MAH));

        int seconds = FpvBattery.armedSeconds();
        rightText(COLS - EDGE_COL, ROW_TIMER,
                glyph(SYM_FLY_TIME) + String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));

        if (!drone.isArmed()) {
            centre(ROW_FUSE, DroneControl.isArmRefused() ? "THROTTLE" : "DISARMED");
        } else if (DroneControl.isArmedFlash()) {
            centre(ROW_FUSE, "ARMED");
        } else if (cell < FpvPack.CELL_LAND) {
            if (blink) {
                centre(ROW_FUSE, "LAND NOW");
            }
        } else if (cell < FpvPack.CELL_LOW) {
            if (blink) {
                centre(ROW_FUSE, "LOW BATTERY");
            }
        } else if (seconds >= FUSE_ARM_SECONDS && blink) {
            centre(ROW_FUSE, "WAIT FUSE2");
        }
    }

    /** How far left of centre the warning sits, in characters. */
    private static final float RXLOSS_LEFT_OF_CENTRE = 4.0F;

    private static String lossVolts;
    private static String lossMah;
    private static String lossTimer;
    private static String lossFuse;
    /** Which OSD the airframe that lost its link was flown on, or NONE for a bare picture. */
    private static DroneEntity.Osd lossStyle = DroneEntity.Osd.BETAFLIGHT;

    /** Whether the airframe that lost its link sent analog video: static then, not a held frame. */
    private static boolean lossAnalog;

    /** The fixed-wing screen, drawn through this same layer when the airframe flies on it. */
    private static final InavOsd INAV = new InavOsd();
    /** The GPS quad's screen, likewise. */
    private static final GpsBetaflightOsd GPS = new GpsBetaflightOsd();
    /** The attitude the horizon froze on. */
    private static float lossPitch;
    private static float lossRoll;

    /** Called by {@code DroneControl} with the drone still in hand, right before the feed dies. */
    public static void captureSignalLoss(DroneEntity drone) {
        lossVolts = glyph(batterySymbol(drone.getCharge()))
                + String.format(Locale.ROOT, "%.2f", FpvBattery.cellVolts()) + glyph(SYM_VOLTS);
        lossMah = FpvBattery.mahDrawn(drone) + glyph(SYM_MAH);
        int seconds = FpvBattery.armedSeconds();
        lossTimer = glyph(SYM_FLY_TIME)
                + String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        lossFuse = drone.isArmed() ? "WAIT FUSE2" : "DISARMED";
        lossStyle = drone.osdStyle();
        lossAnalog = drone instanceof dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity analog
                && analog.analogVideo();
        if (lossStyle == DroneEntity.Osd.INAV) {
            InavOsd.captureSignalLoss(drone);
        } else if (lossStyle == DroneEntity.Osd.BETAFLIGHT_GPS) {
            GpsBetaflightOsd.captureSignalLoss(drone);
        }
        lossPitch = drone.bodyPitch(1.0F);
        lossRoll = drone.bodyRoll(1.0F);
    }

    private void renderSignalLost() {
        drawHorizon(ROW_HORIZON, lossPitch, lossRoll);
        drawCrosshair();
        text(EDGE_COL, ROW_MODE, "ACRO");
        centre(ROW_MODE, SkyfrontConfig.INTERCEPTOR_CRAFT_NAME.get().toUpperCase(Locale.ROOT));
        rightText(COLS - EDGE_COL, ROW_MODE, glyph(SYM_RSSI) + RSSI_WORST + ":2");
        rightText(COLS - EDGE_COL, ROW_RATE, "LQ" + LQ_RATE + ":0");
        if (lossVolts != null) {
            text(EDGE_COL, ROW_VOLTS, lossVolts);
        }
        if (lossMah != null) {
            text(EDGE_COL, ROW_MAH, lossMah);
        }
        if (lossTimer != null) {
            rightText(COLS - EDGE_COL, ROW_TIMER, lossTimer);
        }
        if (lossFuse != null) {
            centre(ROW_FUSE, lossFuse);
        }
        offsetFromCentre(ROW_FUSE - 1, "RX LOSS", -RXLOSS_LEFT_OF_CENTRE);
    }

    /**
     * The crosshair: a chevron of five dashes in three rows narrowing downwards — two set wide apart, two
     * almost meeting, one in the middle — pointing at the spot the camera is looking at.
     */
    private void drawCrosshair() {
        float centreX = this.originX + (CENTRE_COL + 0.5F) * this.glyphW;
        float centreY = this.originY + (ROW_HORIZON + 0.5F) * this.glyphH;

        this.g.pose().pushPose();
        this.g.pose().translate(centreX, centreY, 0.0F);
        this.g.pose().scale(this.glyphW / GLYPH_W, this.glyphH / GLYPH_H, 1.0F);
        for (int[] dash : CROSSHAIR) {
            this.g.fill(dash[0] - 1, dash[2], dash[1] + 1, dash[2] + 2, 0xFF000000);
        }
        for (int[] dash : CROSSHAIR) {
            this.g.fill(dash[0], dash[2], dash[1], dash[2] + 1, 0xFFFFFFFF);
        }
        this.g.pose().popPose();
    }

    /** The chevron's dashes as {left, right, row} in font pixels about the aiming point. */
    private static final int[][] CROSSHAIR = {
            {-9, -4, -3}, {4, 9, -3},
            {-6, -1, 0}, {1, 6, 0},
            {-3, 3, 3},
    };

    /** The battery glyph empties as the pack does: full at 0x90, flat at 0x96. */
    private static int batterySymbol(float charge) {
        int step = Math.round((1.0F - Mth.clamp(charge, 0.0F, 1.0F)) * 6.0F);
        return Mth.clamp(SYM_BATTERY_FULL + step, SYM_BATTERY_FULL, SYM_BATTERY_EMPTY);
    }
}
