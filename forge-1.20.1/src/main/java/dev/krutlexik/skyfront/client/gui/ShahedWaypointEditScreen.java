package dev.krutlexik.skyfront.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Manual waypoint entry: exact X/Z plus the point's number (its position in the route). */
@OnlyIn(Dist.CLIENT)
public class ShahedWaypointEditScreen extends Screen {
    private static final int PANEL_W = 200;

    private final ShahedRouteScreen parent;
    /** Index of the point being edited, or -1 to add a new one. */
    private final int editIndex;
    private boolean farDive;

    private EditBox xInput;
    private EditBox zInput;
    private EditBox numberInput;
    /** Set when the typed coordinates fall outside permitted airspace, to explain the refusal. */
    private boolean rejected;

    public ShahedWaypointEditScreen(ShahedRouteScreen parent, int editIndex) {
        super(Component.translatable(editIndex >= 0 ? "gui.sfw.waypoint.edit" : "gui.sfw.waypoint.add"));
        this.parent = parent;
        this.editIndex = editIndex;
        this.farDive = parent.isFarDive();
    }

    private boolean editingFinal() {
        return this.editIndex >= 0 && this.editIndex == this.parent.waypointCount() - 1;
    }

    private int panelLeft() {
        return this.width / 2 - PANEL_W / 2;
    }

    private int panelTop() {
        return this.height / 2 - 64;
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = panelTop();

        int[] existing = this.editIndex >= 0 ? this.parent.waypoint(this.editIndex) : null;
        int defaultNumber = this.editIndex >= 0 ? this.editIndex + 1 : this.parent.waypointCount() + 1;

        this.xInput = this.makeInput(left + 70, top + 24, existing != null ? String.valueOf(existing[0]) : "0", true);
        this.zInput = this.makeInput(left + 70, top + 44, existing != null ? String.valueOf(existing[1]) : "0", true);
        this.numberInput = this.makeInput(left + 70, top + 64, String.valueOf(defaultNumber), false);
        this.addRenderableWidget(this.xInput);
        this.addRenderableWidget(this.zInput);
        this.addRenderableWidget(this.numberInput);

        int y = top + 84;
        if (this.editingFinal()) {
            this.addRenderableWidget(new TerminalButton(left + 8, y, PANEL_W - 16, 16, this.diveLabel(), b -> {
                this.farDive = !this.farDive;
                b.setMessage(this.diveLabel());
            }));
            y += 20;
        }

        this.addRenderableWidget(new TerminalButton(left + 8, y, (PANEL_W - 20) / 2, 18,
                Component.translatable("gui.sfw.waypoint.ok"), b -> this.confirm()));
        this.addRenderableWidget(new TerminalButton(left + PANEL_W / 2 + 2, y, (PANEL_W - 20) / 2, 18,
                Component.translatable("gui.sfw.waypoint.cancel"), b -> this.onClose()));
    }

    private Component diveLabel() {
        return Component.translatable("gui.sfw.control.dive",
                Component.translatable(this.farDive ? "gui.sfw.control.dive.far" : "gui.sfw.control.dive.near"));
    }

    private EditBox makeInput(int x, int y, String value, boolean signed) {
        EditBox box = new EditBox(this.font, x + 3, y + 3, PANEL_W - 82, 12, Component.empty());
        box.setBordered(false);
        box.setMaxLength(8);
        box.setTextColor(0xFFB8FFCB);
        box.setValue(value);
        box.setFilter(s -> s.isEmpty() || (signed ? s.matches("-?\\d*") : s.matches("\\d*")));
        return box;
    }

    private void confirm() {
        int x = parseOr(this.xInput.getValue(), 0);
        int z = parseOr(this.zInput.getValue(), 0);
        int count = this.parent.waypointCount();

        if (!this.parent.isAllowedPosition(x, z)) {
            this.rejected = true;
            return;
        }

        if (this.editIndex >= 0) {
            int number = Mth.clamp(parseOr(this.numberInput.getValue(), this.editIndex + 1), 1, count);
            this.parent.setWaypoint(this.editIndex, x, z);
            if (number - 1 != this.editIndex) {
                this.parent.moveWaypoint(this.editIndex, number - 1);
            }
            if (this.editingFinal()) {
                this.parent.setFarDive(this.farDive);
            }
        } else {
            int number = Mth.clamp(parseOr(this.numberInput.getValue(), count + 1), 1, count + 1);
            this.parent.addWaypoint(x, z, number - 1);
        }
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void tick() {
        super.tick();
        this.xInput.tick();
        this.zInput.tick();
        this.numberInput.tick();
    }

    private static int parseOr(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int left = panelLeft();
        int top = panelTop();
        int bottom = top + (this.editingFinal() ? 126 : 106);

        ShahedTerminalScreen.drawPanel(graphics, left, top, left + PANEL_W, bottom);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 6, 0xFF43FF6B);
        graphics.fill(left + 8, top + 18, left + PANEL_W - 8, top + 19, 0xFF1E7A34);

        graphics.drawString(this.font, Component.translatable("gui.sfw.control.x"), left + 10, top + 27, 0xFF43FF6B);
        graphics.drawString(this.font, Component.translatable("gui.sfw.control.z"), left + 10, top + 47, 0xFF43FF6B);
        graphics.drawString(this.font, Component.translatable("gui.sfw.waypoint.number"), left + 10, top + 67, 0xFF43FF6B);

        drawInputFrame(graphics, left + 70, top + 24, left + PANEL_W - 10, top + 40);
        drawInputFrame(graphics, left + 70, top + 44, left + PANEL_W - 10, top + 60);
        drawInputFrame(graphics, left + 70, top + 64, left + PANEL_W - 10, top + 80);

        if (this.rejected) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.sfw.waypoint.forbidden"),
                    this.width / 2, top + 84 + (this.editingFinal() ? 20 : 0) - 12, 0xFFFF5555);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static void drawInputFrame(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0, y0, x1, y1, 0xFF03140A);
        graphics.fill(x0, y0, x1, y0 + 1, 0xFF1E7A34);
        graphics.fill(x0, y1 - 1, x1, y1, 0xFF1E7A34);
        graphics.fill(x0, y0, x0 + 1, y1, 0xFF1E7A34);
        graphics.fill(x1 - 1, y0, x1, y1, 0xFF1E7A34);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
