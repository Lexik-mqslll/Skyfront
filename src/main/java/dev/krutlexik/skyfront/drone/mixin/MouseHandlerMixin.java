package dev.krutlexik.skyfront.drone.mixin;

import dev.krutlexik.skyfront.drone.client.DroneControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    /** GLFW mouse buttons, and the press action (release is 0). */
    private static final int LEFT_BUTTON = 0;
    private static final int RIGHT_BUTTON = 1;
    private static final int PRESSED = 1;

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

    @Inject(method = "turnPlayer(D)V", at = @At("HEAD"), cancellable = true)
    private void skyfront$steerDrone(double movementTime, CallbackInfo callbackInfo) {
        if (!DroneControl.holdsTheScreen()) {
            return;
        }
        if (!DroneControl.isActive()) {
            callbackInfo.cancel();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        double sensitivity = minecraft.options.sensitivity().get() * 0.6D + 0.2D;
        double scale = sensitivity * sensitivity * sensitivity * 8.0D * 0.15D;
        double invertY = minecraft.options.invertYMouse().get() ? -1.0D : 1.0D;
        DroneControl.steer(this.accumulatedDX * scale, this.accumulatedDY * scale * invertY,
                this.accumulatedDX, this.accumulatedDY * invertY, movementTime);

        callbackInfo.cancel();
    }
}
