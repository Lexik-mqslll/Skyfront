package dev.krutlexik.skyfront.drone.mixin;

import dev.krutlexik.skyfront.drone.client.DroneControl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps entities drawn out to the far end of a drone feed. */
@Mixin(Entity.class)
public class EntityRenderRangeMixin {
    /** Blocks. */
    private static final double DRONE_RENDER_RANGE = 320.0D;

    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
    private void skyfront$reachForDroneCamera(double distance, CallbackInfoReturnable<Boolean> cir) {
        if (DroneControl.isActive()) {
            cir.setReturnValue(distance < DRONE_RENDER_RANGE * DRONE_RENDER_RANGE);
        }
    }
}
