package dev.krutlexik.skyfront.drone.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;

import java.util.ArrayList;
import java.util.List;

/**
 * The little "THERMAL" tag in the top left corner of a thermal drone's slot, so the thermal airframes can be
 * told from their plain twins at a glance — the two are otherwise the very same model.
 */
public final class ThermalBadge implements IItemDecorator {
    private static final int BACK = 0xF0100010;
    private static final int BORDER_TOP = 0x505000FF;
    private static final int BORDER_BOTTOM = 0x5028007F;
    private static final int TEXT = 0xFFFFFFFF;

    private static final String WORD = "THERMAL";
    private static final String[][] GLYPHS = {
            {"###", ".#.", ".#.", ".#.", ".#."},
            {"#.#", "#.#", "###", "#.#", "#.#"},
            {"##", "#.", "##", "#.", "##"},
            {"##.", "#.#", "##.", "#.#", "#.#"},
            {"#...#", "##.##", "#.#.#", "#...#", "#...#"},
            {".#.", "#.#", "###", "#.#", "#.#"},
            {"#.", "#.", "#.", "#.", "##"},
    };
    private static final int ROWS = 5;

    /** The letters as runs of lit pixels, {x0, y, x1}, in half-GUI pixels from the tag's corner. */
    private static final int[][] RUNS;
    /** The tag's size, half-GUI pixels: the letters plus a one-pixel border all round. */
    private static final int WIDTH;
    private static final int HEIGHT = ROWS + 2;

    static {
        List<int[]> runs = new ArrayList<>();
        int x = 1;
        for (String[] glyph : GLYPHS) {
            for (int row = 0; row < ROWS; row++) {
                String line = glyph[row];
                int start = -1;
                for (int col = 0; col <= line.length(); col++) {
                    boolean lit = col < line.length() && line.charAt(col) == '#';
                    if (lit && start < 0) {
                        start = col;
                    } else if (!lit && start >= 0) {
                        runs.add(new int[] {x + start, 1 + row, x + col});
                        start = -1;
                    }
                }
            }
            x += glyph[0].length() + 1;
        }
        RUNS = runs.toArray(new int[0][]);
        WIDTH = x;
    }

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        graphics.pose().pushPose();
        graphics.pose().translate(xOffset, yOffset, 200.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);

        int right = WIDTH - 1;
        int bottom = HEIGHT - 1;
        graphics.fill(1, 0, right, HEIGHT, BACK);
        graphics.fill(0, 1, 1, bottom, BACK);
        graphics.fill(right, 1, WIDTH, bottom, BACK);
        graphics.fill(1, 0, right, 1, BORDER_TOP);
        graphics.fill(1, bottom, right, HEIGHT, BORDER_BOTTOM);
        graphics.fillGradient(0, 1, 1, bottom, BORDER_TOP, BORDER_BOTTOM);
        graphics.fillGradient(right, 1, WIDTH, bottom, BORDER_TOP, BORDER_BOTTOM);

        for (int[] run : RUNS) {
            graphics.fill(run[0], run[1], run[2], run[1] + 1, TEXT);
        }
        graphics.pose().popPose();
        return false;
    }
}
