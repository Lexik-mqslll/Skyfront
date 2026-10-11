package dev.krutlexik.skyfront.client.gui;

import dev.krutlexik.skyfront.network.NetworkHandler;
import dev.krutlexik.skyfront.network.TerminalActionPacket;
import dev.krutlexik.skyfront.network.TerminalDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal main screen: the list of bound Shahed-136 drones with live status, a per-drone "Control" button
 * (opens {@link ShahedRouteScreen}) and an unbind button.
 */
@OnlyIn(Dist.CLIENT)
public class ShahedTerminalScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 6;
    private static final int PANEL_W = 340;

    private final BlockPos terminalPos;
    private List<TerminalDataPacket.Row> rows;
    private int scrollOffset = 0;
    private int refreshTimer = 0;
    private boolean draggingScroll = false;

    public ShahedTerminalScreen(BlockPos terminalPos, List<TerminalDataPacket.Row> rows) {
        super(Component.translatable("gui.sfw.terminal.title"));
        this.terminalPos = terminalPos;
        this.rows = new ArrayList<>(rows);
    }

    public BlockPos getTerminalPos() {
        return this.terminalPos;
    }

    /** Fresh data from the server: update and rebuild the row widgets. */
    public void updateData(List<TerminalDataPacket.Row> rows) {
        this.rows = new ArrayList<>(rows);
        if (this.scrollOffset > Math.max(0, this.rows.size() - VISIBLE_ROWS)) {
            this.scrollOffset = Math.max(0, this.rows.size() - VISIBLE_ROWS);
        }
        this.rebuildWidgets();
    }

    private int panelLeft() {
        return this.width / 2 - PANEL_W / 2;
    }

    private int listTop() {
        return this.height / 2 - 62;
    }

    private static final int SCROLLBAR_W = 5;

    private int maxScroll() {
        return Math.max(0, this.rows.size() - VISIBLE_ROWS);
    }

    private boolean needsScrollbar() {
        return this.maxScroll() > 0;
    }

    /** Track sits flush against the panel's inner right edge, never poking outside it. */
    private int trackLeft() {
        return this.panelLeft() + PANEL_W - SCROLLBAR_W;
    }

    private int trackHeight() {
        return VISIBLE_ROWS * ROW_HEIGHT - 4;
    }

    private int thumbHeight() {
        return Math.max(20, this.trackHeight() * VISIBLE_ROWS / Math.max(1, this.rows.size()));
    }

    private int thumbY() {
        int travel = this.trackHeight() - this.thumbHeight();
        return this.listTop() + (this.maxScroll() == 0 ? 0 : travel * this.scrollOffset / this.maxScroll());
    }

    /** Maps a mouse Y to a scroll offset, grabbing the thumb by its middle. */
    private void scrollToMouse(double mouseY) {
        int travel = this.trackHeight() - this.thumbHeight();
        if (travel <= 0) {
            return;
        }
        double rel = mouseY - this.listTop() - this.thumbHeight() / 2.0;
        int target = (int) Math.round(rel / travel * this.maxScroll());
        int clamped = Mth.clamp(target, 0, this.maxScroll());
        if (clamped != this.scrollOffset) {
            this.scrollOffset = clamped;
            this.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        int listTop = this.listTop();
        int right = this.panelLeft() + PANEL_W - SCROLLBAR_W - 4;

        int shown = Math.min(VISIBLE_ROWS, this.rows.size() - this.scrollOffset);
        for (int i = 0; i < shown; i++) {
            TerminalDataPacket.Row row = this.rows.get(this.scrollOffset + i);
            int y = listTop + i * ROW_HEIGHT;
            if (row.status() != TerminalDataPacket.STATUS_NO_SIGNAL) {
                this.addRenderableWidget(new TerminalButton(right - 122, y, 96, 20,
                        Component.translatable("gui.sfw.terminal.manage"),
                        b -> this.minecraft.setScreen(new ShahedRouteScreen(this, this.terminalPos, row, this.rows))));
            }
            this.addRenderableWidget(new TerminalButton(right - 20, y, 20, 20,
                    Component.literal("✕"),
                    b -> NetworkHandler.sendToServer(
                            TerminalActionPacket.unbind(this.terminalPos, row.uuid()))));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && this.needsScrollbar()
                && mouseX >= this.trackLeft() && mouseX <= this.trackLeft() + SCROLLBAR_W
                && mouseY >= this.listTop() && mouseY <= this.listTop() + this.trackHeight()) {
            this.draggingScroll = true;
            this.scrollToMouse(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScroll && button == 0) {
            this.scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingScroll = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.needsScrollbar()) {
            int next = Mth.clamp(this.scrollOffset - (int) Math.signum(scrollY), 0, this.maxScroll());
            if (next != this.scrollOffset) {
                this.scrollOffset = next;
                this.rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.refreshTimer >= 40) {
            this.refreshTimer = 0;
            NetworkHandler.sendToServer(TerminalActionPacket.refresh(this.terminalPos));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBlurredBackground(partialTick);
        int left = this.panelLeft();
        int listTop = this.listTop();
        int top = listTop - 30;
        int screenTop = listTop - 6;
        int screenBottom = listTop + VISIBLE_ROWS * ROW_HEIGHT + 2;
        int bottom = screenBottom + 21;

        ConsoleStyle.panel(graphics, left - 10, top, left + PANEL_W + 10, bottom);
        ConsoleStyle.plate(graphics, this.font, this.width / 2, top + 6, this.title);
        ConsoleStyle.crt(graphics, left - 2, screenTop, left + PANEL_W + 2, screenBottom);

        if (this.rows.isEmpty()) {
            var lines = this.font.split(Component.translatable("gui.sfw.terminal.empty"), PANEL_W - 24);
            int y = (screenTop + screenBottom) / 2 - lines.size() * 11 / 2;
            for (var line : lines) {
                graphics.drawCenteredString(this.font, line, this.width / 2, y, ConsoleStyle.PHOSPHOR);
                y += 11;
            }
        }

        int shown = Math.min(VISIBLE_ROWS, this.rows.size() - this.scrollOffset);
        for (int i = 0; i < shown; i++) {
            TerminalDataPacket.Row row = this.rows.get(this.scrollOffset + i);
            int y = listTop + i * ROW_HEIGHT + 6;
            graphics.drawString(this.font,
                    Component.translatable("gui.sfw.terminal.entry", row.number()).getString(), left + 4, y, 0xFFE0E0E0);
            graphics.drawString(this.font, statusText(row).getString(), left + 78, y, statusColor(row.status()));
            if (i < shown - 1) {
                graphics.fill(left + 2, y + 17, left + PANEL_W - SCROLLBAR_W - 4, y + 18, ConsoleStyle.PHOSPHOR_DIM);
            }
        }

        ConsoleStyle.lamps(graphics, left - 2, bottom - 13, ConsoleStyle.LAMP_GREEN, ConsoleStyle.LAMP_GREEN,
                ConsoleStyle.LAMP_AMBER);
        graphics.drawString(this.font, Component.translatable("gui.sfw.terminal.lamps"), left + 22, bottom - 15, 0xFFBEC4A0);
        Component post = Component.literal("ПУ-136");
        ConsoleStyle.plate(graphics, this.font, left + PANEL_W - this.font.width(post) / 2 - 4, bottom - 16, post);

        this.renderScrollbar(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Slim console-style scrollbar: recessed track with a green thumb, sized to the list. */
    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!this.needsScrollbar()) {
            return;
        }
        int x0 = this.trackLeft();
        int x1 = x0 + SCROLLBAR_W;
        int top = this.listTop();
        int bottom = top + this.trackHeight();

        graphics.fill(x0, top, x1, bottom, 0xFF050F07);
        graphics.fill(x0, top, x1, top + 1, ConsoleStyle.PHOSPHOR_DIM);
        graphics.fill(x0, bottom - 1, x1, bottom, ConsoleStyle.PHOSPHOR_DIM);

        int thumbTop = this.thumbY();
        int thumbBottom = thumbTop + this.thumbHeight();
        boolean hovered = this.draggingScroll
                || (mouseX >= x0 && mouseX <= x1 && mouseY >= thumbTop && mouseY <= thumbBottom);
        graphics.fill(x0, thumbTop, x1, thumbBottom, hovered ? ConsoleStyle.PHOSPHOR : ConsoleStyle.PHOSPHOR_DIM);
        graphics.fill(x0, thumbTop, x0 + 1, thumbBottom, ConsoleStyle.PHOSPHOR_BRIGHT);
    }

    /** The console's olive-drab steel panel, shared by every terminal screen (see {@link ConsoleStyle#panel}). */
    static void drawPanel(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        ConsoleStyle.panel(graphics, x0, y0, x1, y1);
    }

    /** Status line only; coordinates are left out to keep clear of the buttons. */
    static Component statusText(TerminalDataPacket.Row row) {
        return switch (row.status()) {
            case TerminalDataPacket.STATUS_READY -> Component.translatable("gui.sfw.terminal.status.ready");
            case TerminalDataPacket.STATUS_FLIGHT -> Component.translatable("gui.sfw.terminal.status.flight");
            case TerminalDataPacket.STATUS_DOWN -> Component.translatable("gui.sfw.terminal.status.down");
            default -> Component.translatable("gui.sfw.terminal.status.no_signal");
        };
    }

    static int statusColor(int status) {
        return switch (status) {
            case TerminalDataPacket.STATUS_READY -> 0xFF55FF55;
            case TerminalDataPacket.STATUS_FLIGHT -> 0xFF55FFFF;
            case TerminalDataPacket.STATUS_DOWN -> 0xFFFF5555;
            default -> 0xFF8A8A8A;
        };
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
