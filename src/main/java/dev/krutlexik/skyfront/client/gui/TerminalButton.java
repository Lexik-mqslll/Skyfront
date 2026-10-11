package dev.krutlexik.skyfront.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A key on the terminal's console (see {@link ConsoleStyle}): a dark rubber cap in a steel well, lit from the
 * top left, with its label in light lettering.
 */
@OnlyIn(Dist.CLIENT)
public class TerminalButton extends Button {
    public enum Style {
        KEY, LIT, LAUNCH
    }

    private Style style = Style.KEY;

    public TerminalButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public TerminalButton style(Style style) {
        this.style = style;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.style == Style.LAUNCH) {
            renderLaunch(graphics);
            return;
        }
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + this.width;
        int y1 = y0 + this.height;
        boolean hover = this.active && this.isHoveredOrFocused();
        boolean lit = this.style == Style.LIT && this.active;

        int cap;
        int highlight;
        int text;
        if (!this.active) {
            cap = 0xFF282928;
            highlight = 0xFF3A3B3A;
            text = 0xFF5C5E5A;
        } else if (lit) {
            cap = hover ? 0xFF2E5430 : 0xFF284628;
            highlight = 0xFF5A965A;
            text = 0xFFAAFFAA;
        } else {
            cap = hover ? 0xFF3C3E3C : 0xFF2E302E;
            highlight = hover ? 0xFF6E706C : 0xFF565854;
            text = hover ? 0xFFFFFFFF : 0xFFE2E4DC;
        }

        graphics.fill(x0, y0, x1, y1, ConsoleStyle.STEEL_0);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, cap);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, highlight);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, highlight);
        graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, 0xFF161716);
        graphics.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, 0xFF161716);

        renderScrollingString(graphics, Minecraft.getInstance().font, this.getMessage(),
                x0 + 3, y0, x1 - 3, y1, text);
    }

    private void renderLaunch(GuiGraphics graphics) {
        int x0 = this.getX();
        int y0 = this.getY();
        int x1 = x0 + this.width;
        int y1 = y0 + this.height;
        for (int y = y0; y < y1; y++) {
            boolean band = y - y0 < 2 || y1 - 1 - y < 2;
            if (band) {
                stripeRun(graphics, x0, x1, y);
            } else {
                stripeRun(graphics, x0, x0 + 2, y);
                stripeRun(graphics, x1 - 2, x1, y);
            }
        }
        boolean hover = this.active && this.isHoveredOrFocused();
        int cap = !this.active ? 0xFF5A2824 : hover ? 0xFFD8281E : 0xFFBE1E18;
        int lit = !this.active ? 0xFF6E3430 : 0xFFF05A4A;
        graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, ConsoleStyle.STEEL_0);
        graphics.fill(x0 + 3, y0 + 3, x1 - 3, y1 - 3, cap);
        graphics.fill(x0 + 3, y0 + 3, x1 - 3, y0 + 4, lit);
        graphics.fill(x0 + 3, y1 - 4, x1 - 3, y1 - 3, 0xFF3C0C0A);
        int text = this.active ? 0xFFFFE6DC : 0xFF96706A;
        graphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                x0 + this.width / 2, y0 + (this.height - 8) / 2, text);
    }

    /** One row of diagonal yellow-and-black stripes from {@code from} to {@code to}. */
    private static void stripeRun(GuiGraphics graphics, int from, int to, int y) {
        int start = from;
        boolean yellow = ((from + y) / 3) % 2 == 0;
        for (int x = from + 1; x <= to; x++) {
            boolean next = x < to && ((x + y) / 3) % 2 == 0;
            if (x == to || next != yellow) {
                graphics.fill(start, y, x, y + 1, yellow ? 0xFFE2B21E : 0xFF181612);
                start = x;
                yellow = next;
            }
        }
    }
}
