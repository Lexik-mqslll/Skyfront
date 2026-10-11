package dev.krutlexik.skyfront.client.gui;

import dev.krutlexik.skyfront.network.NetworkHandler;
import dev.krutlexik.skyfront.network.RequestWorldMapPacket;
import dev.krutlexik.skyfront.network.TerminalActionPacket;
import dev.krutlexik.skyfront.network.TerminalDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Route planner for one drone: a terrain map on the left where the player clicks to lay waypoints, and an
 * info/list panel on the right.
 */
@OnlyIn(Dist.CLIENT)
public class ShahedRouteScreen extends Screen {
    /** Map half-size in blocks — also the drone's max flight range from its launch point. */
    private static final int MAP_RADIUS = 2000;
    private static final int PANEL_W = 150;
    private static final int LIST_ROWS = 6;
    private static final ResourceLocation DRONE_ICON =
            ResourceLocation.fromNamespaceAndPath("sfw", "textures/gui/shahed_icon.png");
    /** On-screen size of the drone marker; the source texture is {@link #ICON_TEX_SIZE} px square. */
    private static final int ICON_SIZE = 24;
    private static final int ICON_TEX_SIZE = 32;

    private final ShahedTerminalScreen parent;
    private final BlockPos terminalPos;
    private TerminalDataPacket.Row row;
    private List<TerminalDataPacket.Row> allRows;
    private final double originX;
    private final double originZ;

    private final TerrainMapView mapView = new TerrainMapView(
            ResourceLocation.fromNamespaceAndPath("sfw", "dynamic/terminal_map"));

    /** Local working copy of the route (flattened x,z), pushed to the server on every change. */
    private final List<int[]> waypoints = new ArrayList<>();
    private int selected = -1;
    private int listScroll = 0;
    private boolean farDive;

    private float zoom = 1.0f;
    private float offsetX;
    private float offsetZ;
    private boolean dragging;
    private static final double PAN_THRESHOLD = 3.0;
    private boolean pressedInMap;
    private boolean pannedWhilePressed;
    private double pressX;
    private double pressY;
    private EditBox altInput;
    private TerminalButton launchButton;
    private int refreshTimer;

    public ShahedRouteScreen(ShahedTerminalScreen parent, BlockPos terminalPos,
                             TerminalDataPacket.Row row, List<TerminalDataPacket.Row> allRows) {
        super(Component.translatable("gui.sfw.route.title", row.number()));
        this.parent = parent;
        this.terminalPos = terminalPos;
        this.row = row;
        this.allRows = allRows;
        this.originX = row.lx();
        this.originZ = row.lz();
        this.farDive = row.farDive();
        readRoute(row.route());
    }

    private void readRoute(int[] flat) {
        this.waypoints.clear();
        for (int i = 0; i + 1 < flat.length; i += 2) {
            this.waypoints.add(new int[]{flat[i], flat[i + 1]});
        }
    }

    public BlockPos getTerminalPos() {
        return this.terminalPos;
    }

    /** Launch point: the center of the map and of the range limit. */
    private double originX() {
        return this.originX;
    }

    private double originZ() {
        return this.originZ;
    }

    /** Route editing is only possible while the drone is still on the ground. */
    private boolean canEdit() {
        return this.row.status() == TerminalDataPacket.STATUS_READY;
    }

    public void updateData(List<TerminalDataPacket.Row> rows) {
        this.allRows = rows;
        for (TerminalDataPacket.Row fresh : rows) {
            if (fresh.uuid().equals(this.row.uuid())) {
                boolean wasEditable = canEdit();
                this.row = fresh;
                if (!java.util.Arrays.equals(fresh.route(), this.flatRoute())) {
                    readRoute(fresh.route());
                    if (this.selected >= this.waypoints.size()) {
                        this.selected = -1;
                    }
                }
                if (wasEditable != canEdit()) {
                    this.rebuildWidgets();
                }
                if (this.launchButton != null) {
                    this.launchButton.active = canEdit() && !this.waypoints.isEmpty();
                }
                return;
            }
        }
        this.minecraft.setScreen(this.parent);
    }

    private int mapSize() {
        return Math.min(this.width - PANEL_W - 40, this.height - 50);
    }

    /** Left edge of the whole layout (map + gap + panel), centred on screen. */
    private int layoutLeft() {
        return (this.width - (mapSize() + 12 + PANEL_W)) / 2;
    }

    private int mapCenterX() {
        return layoutLeft() + mapSize() / 2;
    }

    private int mapCenterY() {
        return this.height / 2;
    }

    private int panelLeft() {
        return layoutLeft() + mapSize() + 12;
    }

    private float scale() {
        return (float) (mapSize() / 2) / (MAP_RADIUS / this.zoom);
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = mapCenterY() - mapSize() / 2;
        int listTop = top + 54;
        int buttonsTop = listTop + LIST_ROWS * 12 + 6;

        this.altInput = new EditBox(this.font, left + 54, buttonsTop + 3, PANEL_W - 58, 12, Component.empty());
        this.altInput.setBordered(false);
        this.altInput.setMaxLength(4);
        this.altInput.setTextColor(ConsoleStyle.AMBER);
        this.altInput.setValue(String.valueOf(this.row.altitude()));
        this.altInput.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
        this.addRenderableWidget(this.altInput);

        boolean editable = canEdit();
        this.altInput.setEditable(editable);

        TerminalButton diveButton = this.addRenderableWidget(new TerminalButton(left, buttonsTop + 18, PANEL_W, 16,
                this.diveLabel(), b -> {
            this.farDive = !this.farDive;
            b.setMessage(this.diveLabel());
            this.pushRoute();
        }).style(TerminalButton.Style.LIT));
        diveButton.active = editable;

        TerminalButton manualButton = this.addRenderableWidget(new TerminalButton(left, buttonsTop + 36, PANEL_W, 16,
                Component.translatable("gui.sfw.route.manual"),
                b -> this.minecraft.setScreen(new ShahedWaypointEditScreen(this, this.selected))));
        manualButton.active = editable;

        TerminalButton deleteButton = this.addRenderableWidget(new TerminalButton(left, buttonsTop + 54, (PANEL_W - 4) / 2, 16,
                Component.translatable("gui.sfw.route.delete"), b -> this.removeSelected()));
        deleteButton.active = editable;
        TerminalButton clearButton = this.addRenderableWidget(new TerminalButton(left + PANEL_W / 2 + 2, buttonsTop + 54, (PANEL_W - 4) / 2, 16,
                Component.translatable("gui.sfw.route.clear"), b -> {
            this.waypoints.clear();
            this.selected = -1;
            this.pushRoute();
        }));
        clearButton.active = editable;

        this.launchButton = this.addRenderableWidget(new TerminalButton(left, buttonsTop + 74, PANEL_W, 18,
                Component.translatable("gui.sfw.control.launch"), b -> {
            this.pushRoute();
            NetworkHandler.sendToServer(new TerminalActionPacket(this.terminalPos,
                    TerminalActionPacket.ACTION_LAUNCH, this.row.uuid(), this.flatRoute(),
                    this.altitudeValue(), this.farDive));
            this.minecraft.setScreen(this.parent);
        }).style(TerminalButton.Style.LAUNCH));
        this.launchButton.active = editable && !this.waypoints.isEmpty();

        this.addRenderableWidget(new TerminalButton(left, buttonsTop + 96, PANEL_W, 16,
                Component.translatable("gui.sfw.control.back"), b -> this.onClose()));

        NetworkHandler.sendToServer(new RequestWorldMapPacket((int) this.originX, (int) this.originZ, MAP_RADIUS));
    }

    private Component diveLabel() {
        return Component.translatable("gui.sfw.control.dive",
                Component.translatable(this.farDive ? "gui.sfw.control.dive.far" : "gui.sfw.control.dive.near"));
    }

    private int altitudeValue() {
        try {
            return Mth.clamp(Integer.parseInt(this.altInput.getValue().trim()), 70,
                    dev.krutlexik.skyfront.client.ClientTerminalLimits.maxAltitude());
        } catch (NumberFormatException e) {
            return this.row.altitude();
        }
    }

    private int[] flatRoute() {
        int[] flat = new int[this.waypoints.size() * 2];
        for (int i = 0; i < this.waypoints.size(); i++) {
            flat[i * 2] = this.waypoints.get(i)[0];
            flat[i * 2 + 1] = this.waypoints.get(i)[1];
        }
        return flat;
    }

    /** Sends the working route to the server, which validates and stores it. */
    private void pushRoute() {
        NetworkHandler.sendToServer(new TerminalActionPacket(this.terminalPos,
                TerminalActionPacket.ACTION_SET_ROUTE, this.row.uuid(), this.flatRoute(),
                this.altitudeValue(), this.farDive));
        if (this.launchButton != null) {
            this.launchButton.active = this.row.status() == TerminalDataPacket.STATUS_READY
                    && !this.waypoints.isEmpty();
        }
    }

    /** Client-side pre-check mirroring the server rules, so bad clicks are simply refused. */
    private boolean canPlace(int x, int z, int atIndex) {
        if (this.waypoints.size() >= dev.krutlexik.skyfront.client.ClientTerminalLimits.maxWaypoints()) {
            return false;
        }
        double dx = x - originX();
        double dz = z - originZ();
        double fromLaunchSq = dx * dx + dz * dz;
        if (fromLaunchSq > (double) MAP_RADIUS * MAP_RADIUS) {
            return false;
        }
        int minLaunch = dev.krutlexik.skyfront.client.ClientTerminalLimits.minLaunchDistance();
        if (fromLaunchSq < (double) minLaunch * minLaunch) {
            return false;
        }
        if (dev.krutlexik.skyfront.client.ClientTerminalLimits.isForbidden(x, z)) {
            return false;
        }
        int prev = atIndex - 1;
        if (prev >= 0 && prev < this.waypoints.size() && dist(this.waypoints.get(prev), x, z) < minSpacing()) {
            return false;
        }
        if (atIndex < this.waypoints.size() && dist(this.waypoints.get(atIndex), x, z) < minSpacing()) {
            return false;
        }
        return true;
    }

    private static double minSpacing() {
        return dev.krutlexik.skyfront.client.ClientTerminalLimits.minSpacing();
    }

    private static double dist(int[] wp, int x, int z) {
        double dx = wp[0] - x;
        double dz = wp[1] - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    void addWaypoint(int x, int z, int atIndex) {
        int index = Mth.clamp(atIndex, 0, this.waypoints.size());
        this.waypoints.add(index, new int[]{x, z});
        this.selected = index;
        this.pushRoute();
    }

    /** True when a coordinate is in permitted airspace (manual entry has to check this too). */
    boolean isAllowedPosition(int x, int z) {
        double dx = x - originX();
        double dz = z - originZ();
        return dx * dx + dz * dz <= (double) MAP_RADIUS * MAP_RADIUS
                && !dev.krutlexik.skyfront.client.ClientTerminalLimits.isForbidden(x, z);
    }

    void setWaypoint(int index, int x, int z) {
        if (index >= 0 && index < this.waypoints.size()) {
            this.waypoints.set(index, new int[]{x, z});
            this.pushRoute();
        }
    }

    /** Moves an existing point to a new position in the order (manual "point number" edit). */
    void moveWaypoint(int from, int to) {
        if (from < 0 || from >= this.waypoints.size()) {
            return;
        }
        int[] wp = this.waypoints.remove(from);
        this.waypoints.add(Mth.clamp(to, 0, this.waypoints.size()), wp);
        this.selected = Mth.clamp(to, 0, this.waypoints.size() - 1);
        this.pushRoute();
    }

    private void removeSelected() {
        if (this.selected >= 0 && this.selected < this.waypoints.size()) {
            this.waypoints.remove(this.selected);
            this.selected = -1;
            this.pushRoute();
        }
    }

    int waypointCount() {
        return this.waypoints.size();
    }

    int[] waypoint(int index) {
        return this.waypoints.get(index);
    }

    boolean isFarDive() {
        return this.farDive;
    }

    void setFarDive(boolean farDive) {
        this.farDive = farDive;
        this.pushRoute();
    }

    private int worldToScreenX(double worldX) {
        return mapCenterX() + Math.round((float) ((worldX - originX() + offsetX) * scale()));
    }

    private int worldToScreenY(double worldZ) {
        return mapCenterY() + Math.round((float) ((worldZ - originZ() + offsetZ) * scale()));
    }

    private boolean inMap(double mouseX, double mouseY) {
        int half = mapSize() / 2;
        return Math.abs(mouseX - mapCenterX()) <= half && Math.abs(mouseY - mapCenterY()) <= half;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int left = panelLeft();
        int listTop = mapCenterY() - mapSize() / 2 + 54;
        if (mouseX >= left && mouseX <= left + PANEL_W && mouseY >= listTop && mouseY < listTop + LIST_ROWS * 12) {
            int index = this.listScroll + (int) ((mouseY - listTop) / 12);
            if (index < this.waypoints.size()) {
                this.selected = index;
            }
            return true;
        }

        if (inMap(mouseX, mouseY)) {
            if (button == 1) {
                this.dragging = true;
                return true;
            }
            if (button == 0) {
                this.pressedInMap = true;
                this.pannedWhilePressed = false;
                this.pressX = mouseX;
                this.pressY = mouseY;
                return true;
            }
        }
        return false;
    }

    /** A left press that never turned into a pan: select the point under it, or place a new one. */
    private void handleMapClick(double mouseX, double mouseY) {
        for (int i = 0; i < this.waypoints.size(); i++) {
            int[] wp = this.waypoints.get(i);
            if (Math.abs(mouseX - worldToScreenX(wp[0])) <= 5 && Math.abs(mouseY - worldToScreenY(wp[1])) <= 5) {
                this.selected = i;
                return;
            }
        }
        if (!canEdit()) {
            return;
        }
        float s = scale();
        int worldX = (int) Math.round(originX() + (mouseX - mapCenterX()) / s - offsetX);
        int worldZ = (int) Math.round(originZ() + (mouseY - mapCenterY()) / s - offsetZ);
        if (canPlace(worldX, worldZ, this.waypoints.size())) {
            addWaypoint(worldX, worldZ, this.waypoints.size());
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) {
            this.dragging = false;
        }
        if (button == 0 && this.pressedInMap) {
            this.pressedInMap = false;
            if (!this.pannedWhilePressed) {
                this.handleMapClick(mouseX, mouseY);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** At zoom 1 the viewport already covers the whole map, so panning is pinned to 0 there. */
    private float maxPan() {
        return Math.max(0.0f, MAP_RADIUS - MAP_RADIUS / this.zoom);
    }

    private void clampPan() {
        float max = maxPan();
        this.offsetX = Mth.clamp(this.offsetX, -max, max);
        this.offsetZ = Mth.clamp(this.offsetZ, -max, max);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.pressedInMap) {
            if (!this.pannedWhilePressed
                    && Math.abs(mouseX - this.pressX) + Math.abs(mouseY - this.pressY) > PAN_THRESHOLD) {
                this.pannedWhilePressed = true;
            }
            if (this.pannedWhilePressed) {
                this.panBy(dragX, dragY);
            }
            return true;
        }
        if (this.dragging && button == 1) {
            this.panBy(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void panBy(double dragX, double dragY) {
        float s = scale();
        this.offsetX += (float) (dragX / s);
        this.offsetZ += (float) (dragY / s);
        clampPan();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inMap(mouseX, mouseY)) {
            this.zoom = scrollY > 0 ? Math.min(this.zoom * 1.2f, 10.0f) : Math.max(this.zoom / 1.2f, 1.0f);
            clampPan();
            return true;
        }
        if (mouseY > mapCenterY() && this.waypoints.size() > LIST_ROWS) {
            this.listScroll = Mth.clamp(this.listScroll - (int) Math.signum(scrollY), 0,
                    this.waypoints.size() - LIST_ROWS);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.refreshTimer >= 10) {
            this.refreshTimer = 0;
            NetworkHandler.sendToServer(TerminalActionPacket.refresh(this.terminalPos));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBlurredBackground(partialTick);
        int size = mapSize();
        int half = size / 2;
        int cx = mapCenterX();
        int cy = mapCenterY();

        ConsoleStyle.panel(graphics, cx - half - 8, cy - half - 19, cx + half + 7, cy + half + 7);
        ConsoleStyle.bezel(graphics, cx - half, cy - half, cx + half, cy + half);
        graphics.fill(cx - half, cy - half, cx + half, cy + half, 0xFF000000);

        this.mapView.render(graphics, cx, cy, half, originX(), originZ(), scale(), offsetX, offsetZ, 0.5f);

        graphics.enableScissor(cx - half, cy - half, cx + half, cy + half);
        renderRangeRing(graphics, cx, cy);
        renderDrones(graphics);
        renderRoute(graphics);
        graphics.disableScissor();

        renderPanel(graphics, mouseX, mouseY);
        ConsoleStyle.plate(graphics, this.font, cx, cy - half - 16, this.title);
        ConsoleStyle.hintStrip(graphics, cx - half - 8, cy + half + 9, cx + half + 7, cy + half + 20);
        graphics.drawString(this.font, Component.translatable("gui.sfw.route.controls"),
                cx - half - 4, cy + half + 11, ConsoleStyle.PHOSPHOR);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The max-range circle: the drone cannot be routed outside it. */
    private void renderRangeRing(GuiGraphics graphics, int cx, int cy) {
        float s = scale();
        int r = Math.round(MAP_RADIUS * s);
        int ox = cx + Math.round(offsetX * s);
        int oy = cy + Math.round(offsetZ * s);
        int segments = 96;
        for (int i = 0; i < segments; i++) {
            double a1 = 2 * Math.PI * i / segments;
            double a2 = 2 * Math.PI * (i + 1) / segments;
            drawLine(graphics, ox + (int) (Math.cos(a1) * r), oy + (int) (Math.sin(a1) * r),
                    ox + (int) (Math.cos(a2) * r), oy + (int) (Math.sin(a2) * r), 0xFF2d5a3d);
        }
    }

    /** Every bound drone with a signal, drawn as its airframe pointing the way it is heading. */
    private void renderDrones(GuiGraphics graphics) {
        for (TerminalDataPacket.Row other : this.allRows) {
            if (other.status() == TerminalDataPacket.STATUS_NO_SIGNAL) {
                continue;
            }
            int x = worldToScreenX(other.ex());
            int y = worldToScreenY(other.ez());
            boolean self = other.uuid().equals(this.row.uuid());

            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0f);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0f - other.yaw()));
            graphics.pose().translate(-ICON_SIZE / 2.0f, -ICON_SIZE / 2.0f, 0.0f);
            if (self) {
                graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                graphics.setColor(0.45f, 0.5f, 0.45f, 0.8f);
            }
            graphics.blit(DRONE_ICON, 0, 0, ICON_SIZE, ICON_SIZE, 0.0f, 0.0f,
                    ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE, ICON_TEX_SIZE);
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            graphics.pose().popPose();

            graphics.drawString(this.font, "#" + other.number(), x + ICON_SIZE / 2 + 2, y - 4,
                    self ? 0xFFE8E8E8 : 0xFF9A9A9A, false);
        }
    }

    /** The route web: drone -> wp1 -> ... -> target (last point red). */
    private void renderRoute(GuiGraphics graphics) {
        int px = worldToScreenX(originX());
        int py = worldToScreenZ_();
        for (int i = 0; i < this.waypoints.size(); i++) {
            int[] wp = this.waypoints.get(i);
            int x = worldToScreenX(wp[0]);
            int y = worldToScreenY(wp[1]);
            boolean last = i == this.waypoints.size() - 1;
            drawLine(graphics, px, py, x, y, last ? 0x99FF5555 : 0x99A0A0A0);
            px = x;
            py = y;

            int color = last ? 0xFFFF4444 : 0xFF55FF55;
            graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
            if (i == this.selected) {
                drawRect(graphics, x - 5, y - 5, x + 6, y + 6, 0xFFFFFFFF);
            }
            graphics.drawString(this.font, String.valueOf(i + 1), x + 5, y - 9, color, false);
        }
    }

    private int worldToScreenZ_() {
        return worldToScreenY(originZ());
    }

    private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = panelLeft();
        int top = mapCenterY() - mapSize() / 2;
        int bottom = top + mapSize() + 2;
        ConsoleStyle.panel(graphics, left - 4, top - 2, left + PANEL_W + 4, bottom);

        int listTop = top + 54;
        int lx0 = left + 3;
        int lx1 = left + PANEL_W - 3;
        int tx = lx0 + 3;
        ConsoleStyle.lcd(graphics, lx0, top + 4, lx1, listTop + LIST_ROWS * 12 + 1);

        graphics.drawString(this.font, Component.translatable("gui.sfw.route.points",
                this.waypoints.size(), dev.krutlexik.skyfront.client.ClientTerminalLimits.maxWaypoints()),
                tx, top + 7, ConsoleStyle.AMBER, false);

        if (this.selected >= 0 && this.selected < this.waypoints.size()) {
            int[] wp = this.waypoints.get(this.selected);
            boolean last = this.selected == this.waypoints.size() - 1;
            graphics.drawString(this.font, Component.translatable("gui.sfw.route.point", this.selected + 1),
                    tx, top + 18, last ? ConsoleStyle.AMBER_RED : ConsoleStyle.AMBER, false);
            graphics.drawString(this.font, "X " + wp[0] + "  Z " + wp[1], tx, top + 28, ConsoleStyle.AMBER, false);
            int d = (int) Math.round(dist(wp, (int) originX(), (int) originZ()));
            graphics.drawString(this.font, Component.translatable("gui.sfw.route.dist", d), tx, top + 38,
                    ConsoleStyle.AMBER_DIM, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.sfw.route.nosel"), tx, top + 18,
                    ConsoleStyle.AMBER_DIM, false);
        }

        for (int i = 0; i < LIST_ROWS; i++) {
            int index = this.listScroll + i;
            if (index >= this.waypoints.size()) {
                break;
            }
            int[] wp = this.waypoints.get(index);
            int y = listTop + i * 12;
            boolean last = index == this.waypoints.size() - 1;
            if (index == this.selected) {
                graphics.fill(lx0, y - 1, lx1, y + 10, 0x40FFB03C);
            }
            graphics.drawString(this.font, (index + 1) + ". " + wp[0] + ", " + wp[1], tx, y,
                    last ? ConsoleStyle.AMBER_RED : ConsoleStyle.AMBER, false);
        }

        int buttonsTop = listTop + LIST_ROWS * 12 + 6;
        Component alt = Component.translatable("gui.sfw.control.alt");
        int plateW = this.font.width(alt) + 10;
        if (plateW <= 48) {
            ConsoleStyle.plate(graphics, this.font, left + plateW / 2, buttonsTop + 2, alt);
        } else {
            graphics.drawString(this.font, alt, left, buttonsTop + 4, 0xFFCEC596);
        }
        ConsoleStyle.lcd(graphics, left + 52, buttonsTop + 1, left + PANEL_W - 3, buttonsTop + 15);

        if (bottom - 12 > buttonsTop + 118) {
            ConsoleStyle.lamps(graphics, left, bottom - 10, ConsoleStyle.LAMP_GREEN, ConsoleStyle.LAMP_AMBER,
                    ConsoleStyle.LAMP_RED_OFF);
        }
    }

    private static void drawRect(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int guard = 0;
        while (guard++ < 8192) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    @Override
    public void onClose() {
        this.pushRoute();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        super.removed();
        this.mapView.close();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
