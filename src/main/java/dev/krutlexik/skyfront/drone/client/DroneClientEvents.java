package dev.krutlexik.skyfront.drone.client;

import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.drone.RateModeFlight;
import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.entity.InterceptorDroneEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.Input;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import dev.krutlexik.skyfront.drone.client.radio.RadioInput;
import dev.krutlexik.skyfront.drone.client.radio.RadioSettingsScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.Set;

@EventBusSubscriber(modid = DroneMod.MODID, value = Dist.CLIENT)
public final class DroneClientEvents {
    /** Fly the drone after the level has ticked, so its old position is already recorded for interpolation. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
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
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        DroneFeedFreeze.newFrame();
        if (DroneControl.isActive()) {
            RadioInput.sampleFrame();
        }
    }

    /** The drone feed is locked to first person: swallow any perspective-toggle (F5) presses. */
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
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
            DroneControl.scrollCameraPitch(event.getScrollDeltaY());
        } else {
            DroneControl.addZoom(event.getScrollDeltaY());
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
        dev.krutlexik.skyfront.drone.client.render.FpvCockpitView.render(
                event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (DroneControl.isActive()
                && dev.krutlexik.skyfront.drone.client.render.IrisCompat.shaderPackInUse()) {
            dev.krutlexik.skyfront.drone.client.render.ThermalPackPass.render(
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!DroneControl.isActive() && !DroneControl.isFpvStatic()) {
            return;
        }
        CAMERA_OSD.render(event.getGuiGraphics(), event.getPartialTick());
        FPV_OSD.render(event.getGuiGraphics(), event.getPartialTick());
    }

    private static final Set<ResourceLocation> HIDDEN_WHILE_FLYING = Set.of(
            VanillaGuiLayers.CROSSHAIR,
            VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.JUMP_METER,
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.AIR_LEVEL,
            VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.TAB_LIST,
            VanillaGuiLayers.SCOREBOARD_SIDEBAR);

    /** The drone feed is a clean camera view: vanilla HUD furniture gets out of the way. */
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if ((DroneControl.isActive() || DroneControl.isFpvStatic())
                && HIDDEN_WHILE_FLYING.contains(event.getName())) {
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
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
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
