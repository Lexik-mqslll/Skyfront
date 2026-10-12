package dev.krutlexik.skyfront.drone.mixin;

import com.mojang.blaze3d.Blaze3D;
import dev.krutlexik.skyfront.drone.client.DroneControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a drone is being flown, the mouse aims the drone instead of the player, who keeps facing wherever
 * they were standing: horizontally it yaws, vertically it tilts the camera gimbal.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;
    @Shadow
    private double lastMouseEventTime;
    @Shadow
    private boolean mouseGrabbed;

    /** GLFW mouse buttons, and the press action (release is 0). */
    private static final int LEFT_BUTTON = 0;
    private static final int RIGHT_BUTTON = 1;
    private static final int PRESSED = 1;

    /** Set while a cursor event, rather than the frame, is turning the player. */
    @Unique
    private boolean skyfront$cursorEvent;

    /** Both mouse buttons while a feed owns the screen, taken at the earliest point there is. */
    @Inject(method = "onPress(JIII)V", at = @At("HEAD"), cancellable = true)
    private void skyfront$droneMouseButton(long windowPointer, int button, int action, int modifiers,
                                      CallbackInfo callbackInfo) {
        if (button == LEFT_BUTTON && action != PRESSED) {
            DroneControl.setReleaseButton(false);
        }
        if (action != PRESSED || !DroneControl.holdsTheScreen()) {
            return;
        }
        if (windowPointer != Minecraft.getInstance().getWindow().getWindow()
                || Minecraft.getInstance().screen != null) {
            return;
        }
        if (DroneControl.isFpvStatic()) {
            if (button == RIGHT_BUTTON) {
                DroneControl.endHeldPicture();
            }
            callbackInfo.cancel();
            return;
        }
        if (button != LEFT_BUTTON) {
            return;
        }
        DroneControl.setReleaseButton(true);
        callbackInfo.cancel();
    }

    /**
     * 1.20.1 turns the player on every cursor event as well as once a frame; the drone takes the movement once a
     * frame, with the frame's time, so a cursor event only adds to what the frame will take.
     */
    @Inject(method = "onMove(JDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;turnPlayer()V"))
    private void skyfront$beginCursorTurn(long windowPointer, double xpos, double ypos, CallbackInfo callbackInfo) {
        this.skyfront$cursorEvent = true;
    }

    @Inject(method = "onMove(JDD)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;turnPlayer()V",
                    shift = At.Shift.AFTER))
    private void skyfront$endCursorTurn(long windowPointer, double xpos, double ypos, CallbackInfo callbackInfo) {
        this.skyfront$cursorEvent = false;
    }

    @Inject(method = "turnPlayer()V", at = @At("HEAD"), cancellable = true)
    private void skyfront$steerDrone(CallbackInfo callbackInfo) {
        if (!DroneControl.holdsTheScreen()) {
            return;
        }
        callbackInfo.cancel();
        if (this.skyfront$cursorEvent) {
            return;
        }

        // 1.20.1's turnPlayer times the frame and clears the accumulated movement itself, so this has to as well.
        double now = Blaze3D.getTime();
        double movementTime = now - this.lastMouseEventTime;
        this.lastMouseEventTime = now;
        Minecraft minecraft = Minecraft.getInstance();
        if (DroneControl.isActive() && this.mouseGrabbed && minecraft.isWindowActive() && minecraft.player != null) {
            double sensitivity = minecraft.options.sensitivity().get() * 0.6D + 0.2D;
            double scale = sensitivity * sensitivity * sensitivity * 8.0D * 0.15D;
            double invertY = minecraft.options.invertYMouse().get() ? -1.0D : 1.0D;
            DroneControl.steer(this.accumulatedDX * scale, this.accumulatedDY * scale * invertY,
                    this.accumulatedDX, this.accumulatedDY * invertY, movementTime);
        }
        this.accumulatedDX = 0.0D;
        this.accumulatedDY = 0.0D;
    }
}
