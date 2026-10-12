package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.RateModeFlight;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * What every flight controller's OSD has in common: an analog camera's 4:3 picture, letterboxed to black
 * inside the game window, with a 30x16 grid of MAX7456 characters laid over it.
 */
public abstract class AnalogOsd {
    protected static final ResourceLocation DEFAULT_FONT = DroneMod.id("textures/gui/osd_font.png");
    private static final int SHEET_W = 207;
    private static final int SHEET_H = 303;
    protected static final int GLYPH_W = 12;
    protected static final int GLYPH_H = 18;
    /** One pixel of gutter between glyphs on the sheet, so cells start every 13 across and 19 down. */
    private static final int PITCH_X = 13;
    private static final int PITCH_Y = 19;

    /** The picture is a 4:3 analog feed; the OSD is a 30x16 character grid laid over it. */
    private static final float FEED_ASPECT = 4.0F / 3.0F;
    protected static final int COLS = 30;
    protected static final int ROWS = 16;
    /** One cell of breathing room from the frame edge. */
    protected static final int EDGE_COL = 1;
    /** The middle cell. */
    protected static final int CENTRE_COL = 15;

    protected GuiGraphics g;
    protected float originX;
    protected float originY;
    protected float glyphW;
    protected float glyphH;
    protected int feedX;
    protected int feedY;
    protected int feedW;
    protected int feedH;

    private static boolean homeKnown;
    private static double homeX;
    private static double homeY;
    private static double homeZ;

    /** Each drone's home, by network id — stable across the copies the client builds of one drone. */
    private static final java.util.Map<Integer, net.minecraft.world.phys.Vec3> HOMES = new java.util.HashMap<>();

    /** Called when the pilot takes a drone: altitude and distance home read from here on. */
    public static void setHome(DroneEntity drone) {
        net.minecraft.world.phys.Vec3 home = HOMES.get(drone.getId());
        if (home == null || drone.isGrounded()) {
            home = drone.position();
            HOMES.put(drone.getId(), home);
        }
        homeX = home.x;
        homeY = home.y;
        homeZ = home.z;
        homeKnown = true;
    }

    public static void clearHome() {
        homeKnown = false;
    }

    /** Height above where the link came up, or the plain world height without a home. */
    protected static double altitude(DroneEntity drone) {
        return homeKnown ? drone.getY() - homeY : drone.getY();
    }

    /** Ground distance back to where the link came up. */
    protected static double homeDistance(DroneEntity drone) {
        if (!homeKnown) {
            return 0.0D;
        }
        double dx = drone.getX() - homeX;
        double dz = drone.getZ() - homeZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The compass bearing from the drone back home, degrees clockwise from north. */
    protected static double homeBearing(DroneEntity drone) {
        double dx = homeX - drone.getX();
        double dz = homeZ - drone.getZ();
        return (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0D) % 360.0D;
    }

    /** The sheet this OSD draws its characters from. */
    protected ResourceLocation font() {
        return DEFAULT_FONT;
    }

    /**
     * The 4:3 picture, centred, with the rest of the window blacked out — an analog camera feed — and the
     * 30x16 character grid laid inside it.
     */
    protected void setUpFrame(GuiGraphics graphics) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        if ((float) width / height > FEED_ASPECT) {
            this.feedH = height;
            this.feedW = Math.round(height * FEED_ASPECT);
        } else {
            this.feedW = width;
            this.feedH = Math.round(width / FEED_ASPECT);
        }
        this.feedX = (width - this.feedW) / 2;
        this.feedY = (height - this.feedH) / 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int black = 0xFF000000;
        if (this.feedX > 0) {
            graphics.fill(0, 0, this.feedX, height, black);
            graphics.fill(this.feedX + this.feedW, 0, width, height, black);
        }
        if (this.feedY > 0) {
            graphics.fill(0, 0, width, this.feedY, black);
            graphics.fill(0, this.feedY + this.feedH, width, height, black);
        }

        this.g = graphics;
        this.glyphH = this.feedH / (float) ROWS;
        this.glyphW = this.glyphH * GLYPH_W / GLYPH_H;
        float gridW = COLS * this.glyphW;
        this.originX = this.feedX + (this.feedW - gridW) / 2.0F;
        this.originY = this.feedY;
    }

    /** A string of glyphs starting at a grid cell. */
    protected void text(int col, int row, String string) {
        drawString(this.originX + col * this.glyphW, this.originY + row * this.glyphH, string);
    }

    /** A string whose last glyph ends at {@code rightCol}. */
    protected void rightText(int rightCol, int row, String string) {
        float x = this.originX + rightCol * this.glyphW - string.length() * this.glyphW;
        drawString(x, this.originY + row * this.glyphH, string);
    }

    /**
     * A string centred on {@code row} — on the crosshair's axis, not the grid's (see {@link #CENTRE_COL}):
     * the crosshair is what the pilot judges the middle of the picture by.
     */
    protected void centre(int row, String string) {
        offsetFromCentre(row, string, 0.0F);
    }

    /** A string hung off the crosshair's axis, {@code offset} characters to one side of it. */
    protected void offsetFromCentre(int row, String string, float offset) {
        float x = this.originX + (CENTRE_COL + 0.5F + offset) * this.glyphW
                - string.length() * this.glyphW / 2.0F;
        drawString(x, this.originY + row * this.glyphH, string);
    }

    /** One glyph at a grid cell. */
    protected void blitCell(int col, int row, int index) {
        blit(this.originX + col * this.glyphW, this.originY + row * this.glyphH, index);
    }

    protected void drawString(float x, float y, String string) {
        for (int i = 0; i < string.length(); i++) {
            blit(x + i * this.glyphW, y, string.charAt(i));
        }
    }

    /** A symbol index as a one-character string, so it can be built into a reading. */
    protected static String glyph(int index) {
        return String.valueOf((char) index);
    }

    protected void blit(float x, float y, int index) {
        if (index <= 0 || index == ' ') {
            return;
        }
        int u = (index % 16) * PITCH_X;
        int v = (index / 16) * PITCH_Y;
        this.g.pose().pushPose();
        this.g.pose().translate(x, y, 0.0F);
        this.g.pose().scale(this.glyphW / GLYPH_W, this.glyphH / GLYPH_H, 1.0F);
        this.g.blit(font(), 0, 0, GLYPH_W, GLYPH_H, u, v, GLYPH_W, GLYPH_H, SHEET_W, SHEET_H);
        this.g.pose().popPose();
    }

    /**
     * Strokes in the font's own pixels, about a point on the screen: a white line one font pixel wide with
     * the black edge the glyphs carry, so drawn furniture — tapes, ladders, the aircraft symbol — has the
     * same weight as the characters around it and reads over sky and snow alike.
     */
    protected void strokes(float centreX, float centreY, int[][] lines) {
        this.g.pose().pushPose();
        this.g.pose().translate(centreX, centreY, 0.0F);
        this.g.pose().scale(this.glyphW / GLYPH_W, this.glyphH / GLYPH_H, 1.0F);
        for (int[] line : lines) {
            this.g.fill(Math.min(line[0], line[2]) - 1, Math.min(line[1], line[3]) - 1,
                    Math.max(line[0], line[2]) + 2, Math.max(line[1], line[3]) + 2, 0xFF000000);
        }
        for (int[] line : lines) {
            this.g.fill(Math.min(line[0], line[2]), Math.min(line[1], line[3]),
                    Math.max(line[0], line[2]) + 1, Math.max(line[1], line[3]) + 1, 0xFFFFFFFF);
        }
        this.g.pose().popPose();
    }

    /** Screen x of a column's left edge. */
    protected float colX(float col) {
        return this.originX + col * this.glyphW;
    }

    /** Screen y of a row's top edge. */
    protected float rowY(float row) {
        return this.originY + row * this.glyphH;
    }

    /**
     * The nine horizon-line glyphs, {@code 0x80} (dash at the top of the cell) through {@code 0x88} (dash at
     * the bottom).
     */
    private static final int SYM_AH_BAR_TOP = 0x80;

    /** The horizon spans four columns either side of centre and its own nine cells of height. */
    private static final int AH_HALF_COLS = 4;

    /**
     * The artificial horizon, drawn Betaflight's own way: for each column around the centre the height at
     * which the horizon crosses it is worked out from the drone's pitch and roll, then a line glyph is placed
     * in the cell that height falls in, picking the sub-height variant ({@link #SYM_AH_BAR_TOP} + 0..8) so
     * the dashes join into a smooth tilted line.
     */
    protected void drawHorizon(int row, float bodyPitch, float bodyRoll) {
        int maxPitchDeci = SkyfrontConfig.INTERCEPTOR_OSD_MAX_PITCH.get() * 10;
        int maxRollDeci = SkyfrontConfig.INTERCEPTOR_OSD_MAX_ROLL.get() * 10;

        int pitchDeci = Mth.clamp(Math.round(bodyPitch * 10.0F), -maxPitchDeci, maxPitchDeci);
        int rollDeci = Mth.clamp(
                Math.round(RateModeFlight.cameraRoll(bodyRoll) * 10.0F),
                -maxRollDeci, maxRollDeci);
        if (maxPitchDeci > 0) {
            pitchDeci = pitchDeci * 25 / maxPitchDeci;
        }

        int baseRow = row - AH_HALF_COLS;
        for (int x = -AH_HALF_COLS; x <= AH_HALF_COLS; x++) {
            int y = ((-rollDeci * x) / 64) + pitchDeci + 41;
            if (y >= 0 && y <= 81) {
                blitCell(CENTRE_COL + x, baseRow + y / 9, SYM_AH_BAR_TOP + (y % 9));
            }
        }
    }
}
