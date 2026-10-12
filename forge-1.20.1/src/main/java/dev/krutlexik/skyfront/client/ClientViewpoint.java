package dev.krutlexik.skyfront.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Where the player is seeing and hearing the world from: the camera. */
public final class ClientViewpoint {
    public static Vec3 position() {
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        if (camera != null) {
            return camera.position();
        }
        return minecraft.player != null ? minecraft.player.position() : Vec3.ZERO;
    }

    private ClientViewpoint() {
    }
}
