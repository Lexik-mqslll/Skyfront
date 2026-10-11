package dev.krutlexik.skyfront.drone.client.radio;

import dev.krutlexik.skyfront.client.gui.ConsoleStyle;
import dev.krutlexik.skyfront.client.gui.TerminalButton;
import dev.krutlexik.skyfront.drone.radio.RadioProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Setting the radio up: which device it is, which stick is which, which switches do what, and where the
 * gimbals actually reach.
 */
public final class RadioSettingsScreen extends Screen {
    @Nullable
    private final Screen parent;

    private enum Tab { DEVICE, STICKS, SWITCHES }

    /** What the screen is busy with: nothing, finding a control, or taking a calibration. */
    private enum Mode { NORMAL, DETECT, SWEEP, CENTRE }

    private Tab tab = Tab.DEVICE;
    private Mode mode = Mode.NORMAL;

    @Nullable
    private RadioProfile.Detection detection;
    @Nullable
    private Consumer<RadioProfile.Found> onFound;
    private Component prompt = Component.empty();

    @Nullable
    private RadioProfile.Capture capture;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    /** The label column of the page showing: as wide as its widest label, so nothing runs into the keys. */
    private int labelWidth;

    private static final int PAD = 12;
    private static final int ROW = 24;
    private static final int GAP = 3;
    /** Room a recess's bezel takes outside it. */
    private static final int BEZEL = 3;

    private static final int LABEL = 0xFFE2DEC4;
    private static final int LABEL_DIM = 0xFF9C9A84;

    public RadioSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.sfw.radio.title"));
        this.parent = parent;
    }

    private static RadioProfile profile() {
        return RadioInput.profile();
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(440, this.width - 16);
        this.panelHeight = Math.min(258, this.height - 8);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight) / 2);

        if (this.mode != Mode.NORMAL) {
            initOverlay();
            return;
        }

        int tabWidth = (innerWidth() - 2 * GAP) / 3;
        int tabY = this.top + 22;
        addTab(Tab.DEVICE, "gui.sfw.radio.tab.device", this.left + PAD, tabY, tabWidth);
        addTab(Tab.STICKS, "gui.sfw.radio.tab.sticks", this.left + PAD + tabWidth + GAP, tabY, tabWidth);
        addTab(Tab.SWITCHES, "gui.sfw.radio.tab.switches", this.left + PAD + 2 * (tabWidth + GAP), tabY,
                innerWidth() - 2 * (tabWidth + GAP));

        switch (this.tab) {
            case DEVICE -> initDevice();
            case STICKS -> initSticks();
            case SWITCHES -> initSwitches();
        }

        int doneWidth = 100;
        addRenderableWidget(key(Component.translatable("gui.sfw.radio.done"), this.left + (this.panelWidth - doneWidth) / 2,
                bottomRowY(), doneWidth, button -> onClose()));
    }

    private void addTab(Tab which, String key, int x, int y, int width) {
        addRenderableWidget(key(Component.translatable(key), x, y, width, b -> {
            if (this.tab != which) {
                this.tab = which;
                rebuildWidgets();
            }
        }).style(this.tab == which ? TerminalButton.Style.LIT : TerminalButton.Style.KEY));
    }

    private static TerminalButton key(Component label, int x, int y, int width, TerminalButton.OnPress onPress) {
        return new TerminalButton(x, y, width, 20, label, onPress);
    }

    private int contentTop() {
        return this.top + 50;
    }

    private int innerWidth() {
        return this.panelWidth - 2 * PAD;
    }

    private int bottom() {
        return this.top + this.panelHeight;
    }

    /** The row of keys along the bottom of the panel: Done, or the overlays' Cancel and Next. */
    private int bottomRowY() {
        return bottom() - 28;
    }

    /** The lowest a recess may reach, its bezel clear of the bottom row of keys. */
    private int contentBottom() {
        return bottomRowY() - 5 - BEZEL;
    }

    private int widest(String... keys) {
        int widest = 0;
        for (String key : keys) {
            widest = Math.max(widest, this.font.width(Component.translatable(key)));
        }
        return widest;
    }

    private static final int CALIBRATE_W = 88;

    private void initDevice() {
        int x = this.left + PAD;
        int y = contentTop();
        int half = (innerWidth() - GAP) / 2;

        addRenderableWidget(key(enabledLabel(), x, y, half, button -> {
            profile().enabled = !profile().enabled;
            rebuildWidgets();
        }).style(profile().enabled ? TerminalButton.Style.LIT : TerminalButton.Style.KEY));
        addRenderableWidget(key(deadbandLabel(), x + half + GAP, y, innerWidth() - half - GAP, button -> {
            cycleDeadband();
            rebuildWidgets();
        }));

        addRenderableWidget(key(deviceLabel(), x, y + ROW, innerWidth() - CALIBRATE_W - GAP, button -> {
            cycleDevice();
            rebuildWidgets();
        }));
        addRenderableWidget(key(Component.translatable("gui.sfw.radio.calibrate"),
                x + innerWidth() - CALIBRATE_W, y + ROW, CALIBRATE_W, button -> {
                    this.capture = new RadioProfile.Capture();
                    this.mode = Mode.SWEEP;
                    rebuildWidgets();
                }));
    }

    /** The device page's screen: below the two rows of keys, down to the bottom row. */
    private int[] deviceScreen() {
        return new int[] {this.left + PAD + BEZEL, contentTop() + 2 * ROW + 2 + BEZEL,
                this.left + this.panelWidth - PAD - BEZEL, contentBottom()};
    }

    private Component enabledLabel() {
        return Component.translatable("gui.sfw.radio.enabled", onOff(profile().enabled));
    }

    private Component deviceLabel() {
        RadioProfile setup = profile();
        if (setup.deviceName.isEmpty()) {
            return Component.translatable("gui.sfw.radio.device",
                    Component.translatable(RadioInput.devices().isEmpty()
                            ? "gui.sfw.radio.device.nothing"
                            : "gui.sfw.radio.device.none"));
        }
        return Component.translatable("gui.sfw.radio.device", setup.deviceName);
    }

    /** Steps through whatever joysticks are plugged in, one per click. */
    private void cycleDevice() {
        List<RadioInput.Device> devices = RadioInput.devices();
        if (devices.isEmpty()) {
            return;
        }
        RadioProfile setup = profile();
        int current = -1;
        for (int i = 0; i < devices.size(); i++) {
            RadioInput.Device device = devices.get(i);
            if (device.guid().equals(setup.deviceGuid) && device.name().equals(setup.deviceName)) {
                current = i;
                break;
            }
        }
        RadioInput.select(devices.get((current + 1) % devices.size()));
    }

    private static final int[] DEADBANDS = {0, 1, 2, 3, 5, 8, 10, 15};

    private Component deadbandLabel() {
        return Component.translatable("gui.sfw.radio.deadband", Math.round(profile().deadband * 100.0F));
    }

    private void cycleDeadband() {
        int now = Math.round(profile().deadband * 100.0F);
        int next = DEADBANDS[0];
        for (int step : DEADBANDS) {
            if (step > now) {
                next = step;
                break;
            }
        }
        profile().deadband = next / 100.0F;
    }

    private static final String[] STICK_LABELS = {"gui.sfw.radio.channel.roll", "gui.sfw.radio.channel.pitch",
            "gui.sfw.radio.channel.yaw", "gui.sfw.radio.channel.throttle", "gui.sfw.radio.controller"};

    private static final int AXIS_W = 50;
    private static final int REVERSE_W = 76;
    private static final int DETECT_W = 68;

    private void initSticks() {
        RadioProfile setup = profile();
        this.labelWidth = widest(STICK_LABELS) + 10;
        int y = contentTop();
        addChannelRow(y, setup.roll, "gui.sfw.radio.prompt.roll");
        addChannelRow(y + ROW, setup.pitch, "gui.sfw.radio.prompt.pitch");
        addChannelRow(y + 2 * ROW, setup.yaw, "gui.sfw.radio.prompt.yaw");
        addChannelRow(y + 3 * ROW, setup.throttle, "gui.sfw.radio.prompt.throttle");

        addRenderableWidget(key(controllerLabel(), this.left + PAD + this.labelWidth, y + 4 * ROW,
                AXIS_W + REVERSE_W + DETECT_W + 2 * GAP, button -> {
                    profile().gamepad = !profile().gamepad;
                    rebuildWidgets();
                }).style(profile().gamepad ? TerminalButton.Style.LIT : TerminalButton.Style.KEY));
    }

    private static Component controllerLabel() {
        return Component.translatable(profile().gamepad ? "gui.sfw.radio.controller.gamepad"
                : "gui.sfw.radio.controller.radio");
    }

    private int stickRowsEnd() {
        return this.left + PAD + this.labelWidth + AXIS_W + REVERSE_W + DETECT_W + 2 * GAP;
    }

    private void addChannelRow(int y, RadioProfile.Channel channel, String promptKey) {
        int x = this.left + PAD + this.labelWidth;
        addRenderableWidget(key(Component.translatable("gui.sfw.radio.axis", channel.axis + 1), x, y, AXIS_W, button -> {
            int count = Math.max(RadioInput.axes().length, 8);
            channel.axis = (channel.axis + 1) % count;
            rebuildWidgets();
        }));
        x += AXIS_W + GAP;

        addRenderableWidget(key(reverseLabel(channel.reverse), x, y, REVERSE_W, button -> {
            channel.reverse = !channel.reverse;
            rebuildWidgets();
        }).style(channel.reverse ? TerminalButton.Style.LIT : TerminalButton.Style.KEY));
        x += REVERSE_W + GAP;

        addRenderableWidget(key(Component.translatable("gui.sfw.radio.detect"), x, y, DETECT_W, button ->
                beginDetect(Component.translatable(promptKey), false, found -> {
                    channel.axis = found.index();
                    channel.reverse = found.reverse();
                })));
    }

    private static Component reverseLabel(boolean reverse) {
        return Component.translatable("gui.sfw.radio.reverse",
                Component.translatable(reverse ? "gui.sfw.radio.yes" : "gui.sfw.radio.no"));
    }

    private static final String[] AUX_LABELS = {"gui.sfw.radio.aux.arm", "gui.sfw.radio.aux.thermal",
            "gui.sfw.radio.aux.camera", "gui.sfw.radio.aux.self_destruct"};

    private static final int STATE_W = 34;
    private static final int AUX_DETECT_W = 68;
    private static final int AUX_REVERSE_W = 76;
    private static final int AUX_MODE_W = 52;
    private static final int AUX_CLEAR_W = 40;

    private void initSwitches() {
        RadioProfile setup = profile();
        this.labelWidth = Math.max(widest(AUX_LABELS), Math.max(
                this.font.width(Component.translatable("gui.sfw.radio.button", 32)),
                widest("gui.sfw.radio.unassigned"))) + 10;
        int y = contentTop();
        addAuxRow(y, setup.arm, "gui.sfw.radio.prompt.switch", true);
        addAuxRow(y + ROW, setup.thermal, "gui.sfw.radio.prompt.switch", true);
        addAuxRow(y + 2 * ROW, setup.camera, "gui.sfw.radio.prompt.camera", false);
        addAuxRow(y + 3 * ROW, setup.selfDestruct, "gui.sfw.radio.prompt.switch", false);
    }

    /** The keys of a switch row, flush with the panel's right edge. */
    private int auxKeysX() {
        return this.left + this.panelWidth - PAD
                - (AUX_DETECT_W + AUX_REVERSE_W + AUX_MODE_W + AUX_CLEAR_W + 3 * GAP);
    }

    /** Its state readout, just ahead of the keys — never back over the label column. */
    private int stateX() {
        return Math.max(this.left + PAD + this.labelWidth + BEZEL, auxKeysX() - 2 * GAP - BEZEL - STATE_W);
    }

    private void addAuxRow(int y, RadioProfile.Aux aux, String promptKey, boolean hasMode) {
        int x = Math.max(auxKeysX(), stateX() + STATE_W + BEZEL + 2 * GAP);
        addRenderableWidget(key(Component.translatable("gui.sfw.radio.detect"), x, y, AUX_DETECT_W, button ->
                beginDetect(Component.translatable(promptKey), hasMode, found -> {
                    aux.source = found.source();
                    aux.index = found.index();
                    aux.reverse = found.reverse();
                })));
        x += AUX_DETECT_W + GAP;

        TerminalButton reverse = key(reverseLabel(aux.reverse), x, y, AUX_REVERSE_W, button -> {
            aux.reverse = !aux.reverse;
            rebuildWidgets();
        }).style(aux.reverse ? TerminalButton.Style.LIT : TerminalButton.Style.KEY);
        reverse.active = aux.assigned();
        addRenderableWidget(reverse);
        x += AUX_REVERSE_W + GAP;

        if (hasMode) {
            TerminalButton modeButton = key(Component.translatable(aux.mode == RadioProfile.AuxMode.SWITCH
                    ? "gui.sfw.radio.mode.switch" : "gui.sfw.radio.mode.button"), x, y, AUX_MODE_W, button -> {
                aux.mode = aux.mode == RadioProfile.AuxMode.SWITCH
                        ? RadioProfile.AuxMode.BUTTON : RadioProfile.AuxMode.SWITCH;
                rebuildWidgets();
            });
            modeButton.active = aux.assigned();
            addRenderableWidget(modeButton);
        }
        x += AUX_MODE_W + GAP;

        TerminalButton clear = key(Component.translatable("gui.sfw.radio.clear"), x, y, AUX_CLEAR_W, button -> {
            aux.source = RadioProfile.Source.NONE;
            rebuildWidgets();
        });
        clear.active = aux.assigned();
        addRenderableWidget(clear);
    }

    /** The strip of glass under a page's rows that carries its instructions. */
    private int[] helpScreen() {
        int rows = this.tab == Tab.STICKS ? 5 : 4;
        return new int[] {this.left + PAD + BEZEL, contentTop() + rows * ROW + 2 + BEZEL,
                this.left + this.panelWidth - PAD - BEZEL, contentBottom()};
    }

    private void beginDetect(Component prompt, boolean acceptButtons, Consumer<RadioProfile.Found> then) {
        RadioInput.poll();
        this.detection = new RadioProfile.Detection(RadioInput.axes(), RadioInput.buttons(), acceptButtons);
        this.onFound = then;
        this.prompt = prompt;
        this.mode = Mode.DETECT;
        rebuildWidgets();
    }

    private void initOverlay() {
        int width = 100;
        int y = bottomRowY();
        int centre = this.left + this.panelWidth / 2;
        switch (this.mode) {
            case DETECT -> addRenderableWidget(key(Component.translatable("gui.sfw.radio.cancel"),
                    centre - width / 2, y, width, button -> backToNormal()));
            case SWEEP -> {
                addRenderableWidget(key(Component.translatable("gui.sfw.radio.cancel"),
                        centre - width - GAP, y, width, button -> backToNormal()));
                addRenderableWidget(key(Component.translatable("gui.sfw.radio.next"), centre + GAP, y, width, button -> {
                    this.mode = Mode.CENTRE;
                    rebuildWidgets();
                }).style(TerminalButton.Style.LIT));
            }
            case CENTRE -> {
                addRenderableWidget(key(Component.translatable("gui.sfw.radio.cancel"),
                        centre - width - GAP, y, width, button -> backToNormal()));
                addRenderableWidget(key(Component.translatable("gui.sfw.radio.done"), centre + GAP, y, width, button -> {
                    if (this.capture != null) {
                        RadioInput.poll();
                        profile().calibration = this.capture.finish(RadioInput.axes());
                    }
                    backToNormal();
                }).style(TerminalButton.Style.LIT));
            }
            default -> {
            }
        }
    }

    private void backToNormal() {
        this.mode = Mode.NORMAL;
        this.detection = null;
        this.onFound = null;
        this.capture = null;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        RadioInput.poll();
        switch (this.mode) {
            case DETECT -> {
                if (this.detection != null) {
                    RadioProfile.Found found = this.detection.poll(RadioInput.axes(), RadioInput.buttons());
                    if (found != null && this.onFound != null) {
                        this.onFound.accept(found);
                        backToNormal();
                    }
                }
            }
            case SWEEP, CENTRE -> {
                if (this.capture != null) {
                    this.capture.accumulate(RadioInput.axes());
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int right = this.left + this.panelWidth;
        ConsoleStyle.panel(graphics, this.left, this.top, right, bottom());
        ConsoleStyle.plate(graphics, this.font, this.left + this.panelWidth / 2, this.top + 6, this.title);
        boolean connected = RadioInput.connected();
        ConsoleStyle.lamps(graphics, this.left + PAD, bottom() - 15,
                connected ? ConsoleStyle.LAMP_GREEN : ConsoleStyle.LAMP_RED_OFF,
                profile().enabled ? ConsoleStyle.LAMP_AMBER : ConsoleStyle.LAMP_RED_OFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.mode != Mode.NORMAL) {
            renderOverlay(graphics);
            return;
        }
        switch (this.tab) {
            case DEVICE -> renderDevice(graphics);
            case STICKS -> renderSticks(graphics);
            case SWITCHES -> renderSwitches(graphics);
        }
    }

    private void renderDevice(GuiGraphics graphics) {
        int[] s = deviceScreen();
        ConsoleStyle.crt(graphics, s[0], s[1], s[2], s[3]);
        int x = s[0] + 6;
        int y = s[1] + 5;
        boolean connected = RadioInput.connected();
        graphics.drawString(this.font, Component.translatable(connected
                        ? "gui.sfw.radio.status.connected" : "gui.sfw.radio.status.missing"),
                x, y, connected ? ConsoleStyle.PHOSPHOR_BRIGHT : ConsoleStyle.AMBER, false);
        if (!connected) {
            wrap(graphics, Component.translatable("gui.sfw.radio.help.connect"), x, y + 14, s[2] - s[0] - 12,
                    ConsoleStyle.PHOSPHOR);
            return;
        }

        RadioProfile setup = profile();
        float[] axes = RadioInput.axes();
        int gimbalTop = y + 14;
        int size = Math.min(64, s[3] - gimbalTop - 14);
        int gx = x + 4;
        gimbal(graphics, gx, gimbalTop, size, setup.stick(setup.yaw, axes), setup.throttleStick(axes),
                Component.translatable("gui.sfw.radio.gimbal.left"));
        gimbal(graphics, gx + size + 14, gimbalTop, size, setup.stick(setup.roll, axes), setup.stick(setup.pitch, axes),
                Component.translatable("gui.sfw.radio.gimbal.right"));

        int sliderX = gx + 2 * size + 14 + 20;
        graphics.drawString(this.font, Component.translatable("gui.sfw.radio.axes"), sliderX, y, ConsoleStyle.PHOSPHOR, false);
        int shown = 0;
        int maxSliders = Math.max(0, (s[2] - 70 - sliderX) / 14);
        for (int i = 0; i < Math.min(axes.length, 16) && shown < maxSliders; i++) {
            if (i == setup.roll.axis || i == setup.pitch.axis || i == setup.yaw.axis || i == setup.throttle.axis) {
                continue;
            }
            slider(graphics, sliderX + shown * 14, gimbalTop, size, axes[i], i + 1);
            shown++;
        }

        boolean[] buttons = RadioInput.buttons();
        int lampX = s[2] - 62;
        graphics.drawString(this.font, Component.translatable("gui.sfw.radio.buttons"), lampX, y, ConsoleStyle.PHOSPHOR, false);
        int perRow = 8;
        int rows = Math.max(1, Math.min(4, (size + 4) / 9));
        for (int i = 0; i < Math.min(buttons.length, perRow * rows); i++) {
            int lx = lampX + (i % perRow) * 7;
            int ly = gimbalTop + (i / perRow) * 9;
            graphics.fill(lx, ly, lx + 5, ly + 5, 0xFF020804);
            graphics.fill(lx + 1, ly + 1, lx + 4, ly + 4, buttons[i] ? ConsoleStyle.PHOSPHOR_BRIGHT : ConsoleStyle.PHOSPHOR_DIM);
        }
    }

    private void renderSticks(GuiGraphics graphics) {
        RadioProfile setup = profile();
        float[] axes = RadioInput.axes();
        int y = contentTop();
        for (int i = 0; i < STICK_LABELS.length; i++) {
            graphics.drawString(this.font, Component.translatable(STICK_LABELS[i]), this.left + PAD, y + i * ROW + 6, LABEL);
        }

        int x0 = stickRowsEnd() + 8 + BEZEL;
        int x1 = this.left + this.panelWidth - PAD - BEZEL;
        int y0 = y + BEZEL;
        int y1 = y + 5 * ROW - 4 - BEZEL;
        ConsoleStyle.crt(graphics, x0, y0, x1, y1);
        int size = Math.max(16, Math.min(y1 - y0 - 16, (x1 - x0 - 18) / 2));
        int spare = (x1 - x0) - 2 * size - 10;
        int gx = x0 + spare / 2;
        int gy = y0 + Math.max(4, (y1 - y0 - size - 12) / 2);
        gimbal(graphics, gx, gy, size, setup.stick(setup.yaw, axes), setup.throttleStick(axes),
                Component.translatable("gui.sfw.radio.gimbal.left"));
        gimbal(graphics, gx + size + 10, gy, size, setup.stick(setup.roll, axes), setup.stick(setup.pitch, axes),
                Component.translatable("gui.sfw.radio.gimbal.right"));

        help(graphics, RadioInput.connected() ? "gui.sfw.radio.help.sticks" : "gui.sfw.radio.help.nodevice");
    }

    private void renderSwitches(GuiGraphics graphics) {
        RadioProfile setup = profile();
        float[] axes = RadioInput.axes();
        boolean[] buttons = RadioInput.buttons();
        int y = contentTop();
        RadioProfile.Aux[] slots = {setup.arm, setup.thermal, setup.camera, setup.selfDestruct};
        for (int i = 0; i < slots.length; i++) {
            int rowY = y + i * ROW;
            RadioProfile.Aux aux = slots[i];
            graphics.drawString(this.font, Component.translatable(AUX_LABELS[i]), this.left + PAD, rowY + 1, LABEL);
            Component where = !aux.assigned() ? Component.translatable("gui.sfw.radio.unassigned")
                    : Component.translatable(aux.source == RadioProfile.Source.AXIS
                            ? "gui.sfw.radio.axis" : "gui.sfw.radio.button", aux.index + 1);
            graphics.drawString(this.font, where, this.left + PAD, rowY + 11, LABEL_DIM, false);

            int sx = stateX();
            ConsoleStyle.lcd(graphics, sx, rowY + 4, sx + STATE_W, rowY + 16);
            String state;
            int colour;
            if (!aux.assigned()) {
                state = "—";
                colour = ConsoleStyle.AMBER_DIM;
            } else if (aux == setup.camera) {
                state = Math.round(setup.auxLevel(aux, axes) * 90.0F) + "°";
                colour = ConsoleStyle.AMBER;
            } else {
                boolean on = setup.auxOn(aux, axes, buttons);
                state = onOff(on).getString();
                colour = on ? ConsoleStyle.AMBER : ConsoleStyle.AMBER_DIM;
            }
            graphics.drawCenteredString(this.font, state, sx + STATE_W / 2, rowY + 6, colour);
        }
        help(graphics, RadioInput.connected() ? "gui.sfw.radio.help.switches" : "gui.sfw.radio.help.nodevice");
    }

    private void help(GuiGraphics graphics, String key) {
        int[] s = helpScreen();
        if (s[3] - s[1] < 12) {
            return;
        }
        ConsoleStyle.crt(graphics, s[0], s[1], s[2], s[3]);
        wrap(graphics, Component.translatable(key), s[0] + 5, s[1] + 4, s[2] - s[0] - 10, ConsoleStyle.PHOSPHOR,
                (s[3] - s[1] - 4) / 10);
    }

    private void renderOverlay(GuiGraphics graphics) {
        int x0 = this.left + PAD + BEZEL;
        int x1 = this.left + this.panelWidth - PAD - BEZEL;
        int y0 = this.top + 24 + BEZEL;
        int y1 = contentBottom();
        ConsoleStyle.crt(graphics, x0, y0, x1, y1);
        int centre = (x0 + x1) / 2;
        int y = y0 + 8;
        Component heading;
        Component body;
        switch (this.mode) {
            case DETECT -> {
                heading = Component.translatable("gui.sfw.radio.detect");
                body = this.prompt;
            }
            case SWEEP -> {
                heading = Component.translatable("gui.sfw.radio.calibrate");
                body = Component.translatable("gui.sfw.radio.calibrate.sweep");
            }
            default -> {
                heading = Component.translatable("gui.sfw.radio.calibrate");
                body = Component.translatable("gui.sfw.radio.calibrate.centre");
            }
        }
        centred(graphics, heading, centre, y, ConsoleStyle.PHOSPHOR_BRIGHT);
        List<FormattedCharSequence> lines = this.font.split(body, x1 - x0 - 24);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(this.font, lines.get(i), centre - this.font.width(lines.get(i)) / 2,
                    y + 16 + i * 11, ConsoleStyle.PHOSPHOR, false);
        }

        int readoutY = y + 16 + lines.size() * 11 + 10;
        if (!RadioInput.connected()) {
            centred(graphics, Component.translatable("gui.sfw.radio.status.missing"), centre, readoutY, ConsoleStyle.AMBER);
            return;
        }
        if (this.mode == Mode.DETECT) {
            if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
                centred(graphics, Component.translatable("gui.sfw.radio.waiting"), centre, readoutY, ConsoleStyle.PHOSPHOR);
            }
            return;
        }
        if (this.capture != null) {
            float[] axes = RadioInput.axes();
            int shown = Math.min(axes.length, 8);
            int width = 30;
            int total = shown * width + (shown - 1) * GAP;
            int x = centre - total / 2;
            for (int i = 0; i < shown; i++) {
                float travel = Math.min(1.0F, this.capture.travel(i) / 2.0F);
                centred(graphics, Component.literal(String.valueOf(i + 1)), x + width / 2, readoutY, ConsoleStyle.PHOSPHOR);
                spanBar(graphics, x, readoutY + 11, width, 6, travel);
                x += width + GAP;
            }
        }
    }

    /**
     * One stick's gimbal on the glass: a square field with its cross-hairs and a tick at each quarter, the
     * stick a bright dot trailing a faint line back to centre.
     */
    private void gimbal(GuiGraphics graphics, int x, int y, int size, float stickX, float stickY, Component caption) {
        int x1 = x + size;
        int y1 = y + size;
        int cx = x + size / 2;
        int cy = y + size / 2;
        graphics.fill(x, y, x1, y1, 0x30000000);
        graphics.fill(x, y, x1, y + 1, ConsoleStyle.PHOSPHOR_DIM);
        graphics.fill(x, y1 - 1, x1, y1, ConsoleStyle.PHOSPHOR_DIM);
        graphics.fill(x, y, x + 1, y1, ConsoleStyle.PHOSPHOR_DIM);
        graphics.fill(x1 - 1, y, x1, y1, ConsoleStyle.PHOSPHOR_DIM);
        graphics.fill(x + 1, cy, x1 - 1, cy + 1, ConsoleStyle.PHOSPHOR_DIM);
        graphics.fill(cx, y + 1, cx + 1, y1 - 1, ConsoleStyle.PHOSPHOR_DIM);
        for (int k = 1; k < 4; k++) {
            if (k == 2) {
                continue;
            }
            int tx = x + size * k / 4;
            int ty = y + size * k / 4;
            graphics.fill(tx, cy - 2, tx + 1, cy + 3, ConsoleStyle.PHOSPHOR_DIM);
            graphics.fill(cx - 2, ty, cx + 3, ty + 1, ConsoleStyle.PHOSPHOR_DIM);
        }

        int reach = size / 2 - 3;
        int dx = cx + Math.round(clamp(stickX) * reach);
        int dy = cy - Math.round(clamp(stickY) * reach);
        int steps = Math.max(Math.abs(dx - cx), Math.abs(dy - cy));
        for (int k = 0; k < steps; k++) {
            int px = cx + Math.round((dx - cx) * (float) k / steps);
            int py = cy + Math.round((dy - cy) * (float) k / steps);
            graphics.fill(px, py, px + 1, py + 1, ConsoleStyle.PHOSPHOR);
        }
        graphics.fill(dx - 2, dy - 1, dx + 3, dy + 2, ConsoleStyle.PHOSPHOR);
        graphics.fill(dx - 1, dy - 2, dx + 2, dy + 3, ConsoleStyle.PHOSPHOR);
        graphics.fill(dx - 1, dy - 1, dx + 2, dy + 2, ConsoleStyle.PHOSPHOR_BRIGHT);

        int captionWidth = this.font.width(caption);
        if (captionWidth <= size + 10) {
            graphics.drawString(this.font, caption, cx - captionWidth / 2, y1 + 3, ConsoleStyle.PHOSPHOR, false);
        }
    }

    /** An upright slider for an axis that is not a stick: a slot with a bright notch where it sits. */
    private void slider(GuiGraphics graphics, int x, int y, int height, float value, int number) {
        int cx = x + 3;
        graphics.fill(cx - 1, y, cx + 2, y + height, ConsoleStyle.PHOSPHOR_DIM);
        graphics.fill(cx, y + 1, cx + 1, y + height - 1, 0xFF020804);
        int at = y + height / 2 - Math.round(clamp(value) * (height / 2.0F - 2.0F));
        graphics.fill(cx - 3, at - 1, cx + 4, at + 2, ConsoleStyle.PHOSPHOR_BRIGHT);
        String label = String.valueOf(number);
        graphics.drawString(this.font, label, cx - this.font.width(label) / 2 + 1, y + height + 3, ConsoleStyle.PHOSPHOR, false);
    }

    /** A bar for a value that does not centre: filled from the left. */
    private static void spanBar(GuiGraphics graphics, int x, int y, int width, int height, float value) {
        graphics.fill(x, y, x + width, y + height, ConsoleStyle.PHOSPHOR_DIM);
        int reach = Math.round(Math.max(0.0F, Math.min(1.0F, value)) * width);
        if (reach > 0) {
            graphics.fill(x, y, x + reach, y + height, ConsoleStyle.PHOSPHOR_BRIGHT);
        }
    }

    private static float clamp(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }

    private void centred(GuiGraphics graphics, Component text, int centre, int y, int colour) {
        graphics.drawString(this.font, text, centre - this.font.width(text) / 2, y, colour, false);
    }

    private void wrap(GuiGraphics graphics, Component text, int x, int y, int width, int colour) {
        wrap(graphics, text, x, y, width, colour, Integer.MAX_VALUE);
    }

    private void wrap(GuiGraphics graphics, Component text, int x, int y, int width, int colour, int maxLines) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            graphics.drawString(this.font, lines.get(i), x, y + i * 10, colour, false);
        }
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "gui.sfw.radio.on" : "gui.sfw.radio.off");
    }

    @Override
    public void onClose() {
        RadioInput.save();
        RadioInput.rebaseline();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
