package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.RateModeFlight;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.Input;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import dev.krutlexik.skyfront.drone.client.radio.RadioInput;
import dev.krutlexik.skyfront.drone.client.radio.RadioSettingsScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = DroneMod.MODID, value = Dist.CLIENT)
public final class DroneClientEvents {
    /** Fly the drone after the level has ticked, so its old position is already recorded for interpolation. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        boolean flying = DroneControl.isActive();
        while (DroneKeys.ARM.consumeClick()) {
            if (flying) {
                DroneControl.toggleArm();
            }
        }
        while (DroneKeys.THERMAL.consumeClick()) {
            if (flying) {
                DroneControl.toggleThermal();
            }
        }
        while (DroneKeys.SELF_DESTRUCT.consumeClick()) {
            if (flying) {
                DroneControl.selfDestruct();
            }
        }
        while (DroneKeys.RADIO_SETTINGS.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null) {
                minecraft.setScreen(new RadioSettingsScreen(null));
            }
        }
        DroneControl.tick();
    }

    /**
     * The radio, read every rendered frame rather than every tick: a gimbal sampled twenty times a second is
     * far coarser than any radio link, and it is the fast inputs an rateMode pilot makes that get lost.
     */
    @SubscribeEvent
    public static void onRenderFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        DroneFeedFreeze.newFrame();
        if (DroneControl.isActive()) {
            RadioInput.sampleFrame();
        }
    }

    /** The drone feed is locked to first person: swallow any perspective-toggle (F5) presses. */
    @SubscribeEvent
    public static void onClientTickPre(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        if (DroneControl.holdsTheScreen()) {
            Minecraft minecraft = Minecraft.getInstance();
            while (minecraft.options.keyDrop.consumeClick()) {
            }
        }
        if (!DroneControl.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (minecraft.options.keyTogglePerspective.consumeClick()) {
        }
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    /** No chat, commands, or inventory while flying — any of those would free the mouse and drop the feed. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!DroneControl.holdsTheScreen()) {
            return;
        }
        var screen = event.getNewScreen();
        if (screen instanceof ChatScreen
                || screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                || screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            event.setCanceled(true);
        }
    }

    /** The pilot's body stays where it is: swallow all movement input while in drone view. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!DroneControl.holdsTheScreen()) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /**
     * The two interaction keys, while flying: use lands the connection, and attack does nothing to the world
     * at all — the left button is the slung grenade's release, read off the window itself (see {@code
     * MouseHandlerMixin}), and the thermal camera has its own key ({@link DroneKeys#THERMAL}).
     */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!DroneControl.holdsTheScreen()) {
            return;
        }
        event.setSwingHand(false);
        event.setCanceled(true);
        if (DroneControl.isFpvStatic()) {
            if (event.isUseItem()) {
                DroneControl.endHeldPicture();
            }
            return;
        }
        if (event.isUseItem()) {
            DroneControl.requestStop();
        }
    }

    /**
     * Scroll wheel drives the camera instead of the hotbar: a zoom on a camera-app drone, or the gimbal's
     * up/down tilt on an FPV airframe that has no zoom to give (the interceptor).
     */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!DroneControl.isActive()) {
            return;
        }
        DroneEntity drone = DroneControl.drone();
        if (drone != null && !drone.hasCameraZoom()) {
            DroneControl.scrollCameraPitch(event.getScrollDelta());
        } else {
            DroneControl.addZoom(event.getScrollDelta());
        }
        event.setCanceled(true);
    }

    /** How much of the airframe's bank the camera takes on when the drone is merely leaning into what it is doing. */
    private static final float CAMERA_ROLL_SHARE = 0.5F;

    /**
     * Leaning the picture with the drone, and — on an airframe whose camera is bolted to it — tilting it up
     * by however far the camera is mounted (see {@code DroneControl#cameraTilt}).
     */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        DroneEntity drone = DroneControl.drone();
        if (drone == null) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        if (DroneControl.onSticks() && drone instanceof InterceptorDroneEntity interceptor) {
            float[] camera = interceptor.cameraAngles(partialTick, DroneControl.cameraTilt());
            event.setYaw(camera[0]);
            event.setPitch(camera[1]);
            event.setRoll(camera[2]);
            return;
        }
        float roll = drone.bodyRoll(partialTick);
        if (roll != 0.0F) {
            event.setRoll(RateModeFlight.cameraRoll(roll) * CAMERA_ROLL_SHARE);
        }
    }

    /** Zoom is just a narrower field of view. */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (DroneControl.isActive()) {
            double lens = DroneControl.zoom();
            if (event.usedConfiguredFov()) {
                DroneControl.easeZoom();
                lens = DroneControl.zoom();
                DroneEntity drone = DroneControl.drone();
                if (drone != null && drone.osdStyle() == DroneEntity.Osd.CAMERA) {
                    lens *= DroneControl.CAMERA_LENS;
                }
            }
            event.setFOV(event.getFOV() / lens);
        }
    }

    /** The player's arm would otherwise float in the middle of the drone feed. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (DroneControl.holdsTheScreen()) {
            event.setCanceled(true);
        }
    }

    private static final DroneOsd CAMERA_OSD = new DroneOsd();
    private static final BetaflightOsd FPV_OSD = new BetaflightOsd();

    /**
     * The thermal camera under an Iris shader pack: by the time the HUD starts, Iris has laid its finished
     * picture into the main target, and that is what the camera works on.
     */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        renderGuiPre(event.getPartialTick());
    }

    private static void renderGuiPre(float partialTick) {
        dev.krutlexik.skyfront.drone.client.render.FpvCockpitView.render(partialTick);
        if (DroneControl.isActive()
                && dev.krutlexik.skyfront.drone.client.render.IrisCompat.shaderPackInUse()) {
            dev.krutlexik.skyfront.drone.client.render.ThermalPackPass.render(partialTick);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        renderGuiPost(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void renderGuiPost(GuiGraphics graphics, float partialTick) {
        if (!DroneControl.isActive() && !DroneControl.isFpvStatic()) {
            return;
        }
        CAMERA_OSD.render(graphics, partialTick);
        FPV_OSD.render(graphics, partialTick);
    }

    /**
     * The HUD pass the game itself skips. 1.20.1 does not run the GUI at all while {@code hideGui} is set and no
     * screen is open, RenderGuiEvent included, and hideGui is exactly how a flight clears the vanilla HUD away
     * (see {@code DroneControl#updateHudHiding}); so the feed's own HUD is drawn here instead, over the finished
     * frame, set up the way the game sets up its GUI pass.
     */
    @SubscribeEvent
    public static void onRenderFrameEnd(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.hideGui || minecraft.screen != null || minecraft.getOverlay() != null
                || minecraft.level == null || (!DroneControl.isActive() && !DroneControl.isFpvStatic())) {
            return;
        }
        Window window = minecraft.getWindow();
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        minecraft.getMainRenderTarget().bindWrite(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F,
                (float) ((double) window.getWidth() / window.getGuiScale()),
                (float) ((double) window.getHeight() / window.getGuiScale()), 0.0F,
                1000.0F, ForgeHooksClient.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0.0F, 0.0F, 1000.0F - ForgeHooksClient.getGuiFarPlane());
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        renderGuiPre(event.renderTickTime);
        renderGuiPost(graphics, event.renderTickTime);
        graphics.flush();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
    }

    /**
     * The vanilla HUD pieces the flight hides, by their 1.20.1 overlay ids: the experience bar and its level
     * number are one overlay there, the jump meter is jump_bar, the selected item's name item_name, the tab
     * list player_list and the sidebar scoreboard.
     */
    private static final Set<ResourceLocation> HIDDEN_WHILE_FLYING = Set.of(
            VanillaGuiOverlay.CROSSHAIR.id(),
            VanillaGuiOverlay.HOTBAR.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id(),
            VanillaGuiOverlay.JUMP_BAR.id(),
            VanillaGuiOverlay.PLAYER_HEALTH.id(),
            VanillaGuiOverlay.ARMOR_LEVEL.id(),
            VanillaGuiOverlay.FOOD_LEVEL.id(),
            VanillaGuiOverlay.AIR_LEVEL.id(),
            VanillaGuiOverlay.ITEM_NAME.id(),
            VanillaGuiOverlay.PLAYER_LIST.id(),
            VanillaGuiOverlay.SCOREBOARD.id());

    /**
     * Every vanilla HUD piece hideGui put away on 1.21 (all of them but the sleep fade), whether a screen was up
     * or not. 1.20.1 only skips the HUD under hideGui while no screen is open; with one up (the pause menu, the
     * radio setup) it still draws the overlays that do not check hideGui themselves — the vignette, effects,
     * chat, debug text — so a flight puts those away itself.
     */
    private static final Set<ResourceLocation> HIDDEN_UNDER_HIDE_GUI = Arrays.stream(VanillaGuiOverlay.values())
            .filter(overlay -> overlay != VanillaGuiOverlay.SLEEP_FADE)
            .map(VanillaGuiOverlay::id)
            .collect(Collectors.toUnmodifiableSet());

    /** The drone feed is a clean camera view: vanilla HUD furniture gets out of the way. */
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiOverlayEvent.Pre event) {
        if (!DroneControl.isActive() && !DroneControl.isFpvStatic()) {
            return;
        }
        ResourceLocation overlay = event.getOverlay().id();
        if (HIDDEN_WHILE_FLYING.contains(overlay)
                || (Minecraft.getInstance().options.hideGui && HIDDEN_UNDER_HIDE_GUI.contains(overlay))) {
            event.setCanceled(true);
        }
    }

    /**
     * The thermal camera's heat signatures, drawn once the world itself is finished — after the weather, so
     * nothing the level draws can end up on top of a body that is meant to be showing through it.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER
                && dev.krutlexik.skyfront.drone.client.render.IrisCompat.shaderPackInUse()) {
            dev.krutlexik.skyfront.drone.client.render.ThermalPackPass.capture(event);
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            float partialTick = event.getPartialTick();
            dev.krutlexik.skyfront.drone.client.render.ThermalHeatRenderer.render(
                    event.getPoseStack(), event.getCamera(), partialTick);
            dev.krutlexik.skyfront.drone.client.render.ThermalScreenPass.render(partialTick);
        }
    }

    /** Start the motor loop for every drone as it appears; the instance follows and fades itself. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide || !(event.getEntity() instanceof DroneEntity drone)
                || !drone.hasEngineSound()) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(drone.requiresArming()
                ? new InterceptorSoundInstance(drone)
                : new DroneSoundInstance(drone));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DroneControl.stop();
        dev.krutlexik.skyfront.drone.DroneTuning.clear();
    }

    private DroneClientEvents() {
    }
}
