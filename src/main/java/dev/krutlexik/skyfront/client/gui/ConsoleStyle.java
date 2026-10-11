package dev.krutlexik.skyfront.client.gui;

import dev.krutlexik.skyfront.SkyfrontMod;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** How the terminal's screens are drawn: as the control post's own hardware. */
@OnlyIn(Dist.CLIENT)
public final class ConsoleStyle {
    private static final ResourceLocation METAL =
            ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "textures/gui/console_metal.png");
    private static final int METAL_SIZE = 64;

    public static final int STEEL_0 = 0xFF383A38;
    public static final int STEEL_1 = 0xFF4E504D;
    public static final int STEEL_2 = 0xFF666864;
    public static final int STEEL_3 = 0xFF80827D;
    public static final int STEEL_4 = 0xFF9A9C96;
    public static final int STEEL_5 = 0xFFB4B6B0;

    private static final int EDGE_LIT = 0xFF5C6638;
    private static final int EDGE_LIT_2 = 0xFF4A5230;
    private static final int EDGE_DARK = 0xFF1A1D10;
    private static final int EDGE_DARK_2 = 0xFF24281A;

    private static final int KHAKI = 0xFFB5AA78;
    private static final int KHAKI_LIT = 0xFFCEC596;
    private static final int KHAKI_DARK = 0xFF786E48;
    public static final int PLATE_TEXT = 0xFF201E14;

    public static final int PHOSPHOR_BG = 0xFF081C0C;
    public static final int PHOSPHOR_DIM = 0xFF16421C;
    public static final int PHOSPHOR = 0xFF40A048;
    public static final int PHOSPHOR_BRIGHT = 0xFF78E682;
    public static final int AMBER_BG = 0xFF181006;
    public static final int AMBER_ROW = 0xFF1E1408;
    public static final int AMBER = 0xFFFFB03C;
    public static final int AMBER_DIM = 0xFF9A6A28;
    public static final int AMBER_RED = 0xFFFF5A3C;

    public static final int LAMP_GREEN = 0xFF3CDC46;
    public static final int LAMP_AMBER = 0xFFF0AA28;
    public static final int LAMP_RED_OFF = 0xFF5A1814;

    private ConsoleStyle() {
    }

    /**
     * An olive-drab steel panel: the metal tiled across it, a little lighter toward the top, a 2px bevel lit
     * along its top and left, and a bolt in each corner.
     */
    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0 + 3, y0 + 3, x1 + 3, y1 + 3, 0x70000000);
        for (int y = y0; y < y1; y += METAL_SIZE) {
            for (int x = x0; x < x1; x += METAL_SIZE) {
                int w = Math.min(METAL_SIZE, x1 - x);
                int h = Math.min(METAL_SIZE, y1 - y);
                g.blit(METAL, x, y, 0.0F, 0.0F, w, h, METAL_SIZE, METAL_SIZE);
            }
        }
        g.fillGradient(x0, y0, x1, y1, 0x10FFFFFF, 0x00000000);
        g.fill(x0, y0, x1, y0 + 1, EDGE_LIT);
        g.fill(x0, y0 + 1, x1 - 1, y0 + 2, EDGE_LIT_2);
        g.fill(x0, y0, x0 + 1, y1, EDGE_LIT);
        g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, EDGE_LIT_2);
        g.fill(x0, y1 - 1, x1, y1, EDGE_DARK);
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, EDGE_DARK_2);
        g.fill(x1 - 1, y0, x1, y1, EDGE_DARK);
        g.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, EDGE_DARK_2);
        if (x1 - x0 >= 24 && y1 - y0 >= 24) {
            bolt(g, x0 + 4, y0 + 4);
            bolt(g, x1 - 6, y0 + 4);
            bolt(g, x0 + 4, y1 - 6);
            bolt(g, x1 - 6, y1 - 6);
        }
    }

    private static void bolt(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, STEEL_2);
        g.fill(x, y, x + 1, y + 1, STEEL_5);
        g.fill(x + 1, y + 1, x + 2, y + 2, EDGE_DARK);
    }

    /**
     * A khaki name plate centred on {@code centreX}, its top at {@code y}: dark stencilled text, a screw at
     * each end.
     */
    public static void plate(GuiGraphics g, Font font, int centreX, int y, Component text) {
        int w = font.width(text) + 10;
        int x0 = centreX - w / 2;
        int x1 = x0 + w;
        g.fill(x0, y, x1, y + 12, KHAKI);
        g.fill(x0, y, x1, y + 1, KHAKI_LIT);
        g.fill(x0, y, x0 + 1, y + 12, KHAKI_LIT);
        g.fill(x0, y + 11, x1, y + 12, KHAKI_DARK);
        g.fill(x1 - 1, y, x1, y + 12, KHAKI_DARK);
        g.fill(x0 + 2, y + 5, x0 + 3, y + 7, STEEL_1);
        g.fill(x1 - 3, y + 5, x1 - 2, y + 7, STEEL_1);
        g.drawString(font, text, x0 + 5, y + 2, PLATE_TEXT, false);
    }

    /** A steel bezel 3px wide round the recess {@code x0..x1, y0..y1}: lit on top and left, dark on the other two. */
    public static void bezel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int[] lit = {STEEL_4, STEEL_3, STEEL_2};
        int[] dark = {STEEL_1, STEEL_1, STEEL_0};
        for (int k = 0; k < 3; k++) {
            int ox0 = x0 - 3 + k;
            int oy0 = y0 - 3 + k;
            int ox1 = x1 + 3 - k;
            int oy1 = y1 + 3 - k;
            g.fill(ox0, oy0, ox1, oy0 + 1, lit[k]);
            g.fill(ox0, oy0, ox0 + 1, oy1, lit[k]);
            g.fill(ox0, oy1 - 1, ox1, oy1, dark[k]);
            g.fill(ox1 - 1, oy0, ox1, oy1, dark[k]);
        }
    }

    /** CRT glass in a bezel: near-black green, darker toward the edges, with scanlines. */
    public static void crt(GuiGraphics g, int x0, int y0, int x1, int y1) {
        bezel(g, x0, y0, x1, y1);
        g.fill(x0, y0, x1, y1, PHOSPHOR_BG);
        int band = Math.max(4, (y1 - y0) / 6);
        g.fillGradient(x0, y0, x1, y0 + band, 0x60000000, 0x00000000);
        g.fillGradient(x0, y1 - band, x1, y1, 0x00000000, 0x60000000);
        for (int y = y0 + 1; y < y1; y += 2) {
            g.fill(x0, y, x1, y + 1, 0x2A000000);
        }
        g.fill(x0, y0, x1, y0 + 1, 0x80000000);
        g.fill(x0, y0, x0 + 1, y1, 0x70000000);
    }

    /** An amber segment readout in a bezel. */
    public static void lcd(GuiGraphics g, int x0, int y0, int x1, int y1) {
        bezel(g, x0, y0, x1, y1);
        g.fill(x0, y0, x1, y1, AMBER_BG);
        for (int y = y0; y < y1; y += 2) {
            g.fill(x0, y, x1, y + 1, AMBER_ROW);
        }
        g.fill(x0, y0, x1, y0 + 1, 0x80000000);
    }

    /** A row of small status lamps, each in its own dark socket. */
    public static void lamps(GuiGraphics g, int x, int y, int... colours) {
        for (int k = 0; k < colours.length; k++) {
            int lx = x + k * 7;
            g.fill(lx, y, lx + 4, y + 4, EDGE_DARK);
            g.fill(lx + 1, y + 1, lx + 3, y + 3, colours[k]);
            g.fill(lx + 1, y + 1, lx + 2, y + 2, brighten(colours[k]));
        }
    }

    /** A dark strip across the bottom of a screen for a line of hints. */
    public static void hintStrip(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0xD00E100C);
    }

    public static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 80);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + 80);
        int b = Math.min(255, (argb & 0xFF) + 80);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }
}
