package dev.krutlexik.skyfront.client;

import dev.krutlexik.skyfront.block.ModBlocks;
import dev.krutlexik.skyfront.client.renderer.LauncherRenderer;
import dev.krutlexik.skyfront.client.renderer.ShahedItemRenderer;
import dev.krutlexik.skyfront.client.renderer.ShahedTerminalRenderer;
import dev.krutlexik.skyfront.drone.client.render.DroneItemRenderer;
import dev.krutlexik.skyfront.drone.client.render.DroneRenderer;
import dev.krutlexik.skyfront.drone.registry.DroneItems;
import dev.krutlexik.skyfront.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * The custom item renderers, handed out to the items' {@code IClientItemExtensions#getCustomRenderer()}. Each is
 * made the first time it is asked for (they need the game's renderers to exist) and kept from then on; items
 * that draw the same glb share one renderer.
 */
@OnlyIn(Dist.CLIENT)
public final class ItemRenderers {
    /** The Shahed-136 item: its real 3D geo model instead of a flat sprite. */
    private static BlockEntityWithoutLevelRenderer shahed;
    /** The glb-drawn items, by the model they draw. */
    private static final Map<ResourceLocation, DroneItemRenderer> GLB = new HashMap<>();

    public static BlockEntityWithoutLevelRenderer forItem(Item item) {
        if (item == ModItems.SHAHED_136.get()) {
            if (shahed == null) {
                shahed = new ShahedItemRenderer();
            }
            return shahed;
        }
        ResourceLocation model = glbModel(item);
        if (model != null) {
            return GLB.computeIfAbsent(model, DroneItemRenderer::new);
        }
        return Minecraft.getInstance().getItemRenderer().getBlockEntityRenderer();
    }

    /**
     * The glb an item is drawn as, or null. The control post's item is drawn as the post itself, from the same
     * glb its block renders; every drone item is drawn as its own 3D airframe — the very glb the entity
     * renderer flies — instead of a hand-painted sprite.
     */
    private static ResourceLocation glbModel(Item item) {
        if (item == ModBlocks.SHAHED_TERMINAL.get().asItem()) {
            return ShahedTerminalRenderer.MODEL;
        }
        if (item == ModItems.LAUNCHER.get()) {
            return LauncherRenderer.MODEL;
        }
        if (item == DroneItems.DRONE.get() || item == DroneItems.THERMAL_DRONE.get()) {
            return DroneRenderer.RECON_MODEL;
        }
        if (item == DroneItems.INTERCEPTOR_DRONE.get() || item == DroneItems.THERMAL_INTERCEPTOR_DRONE.get()) {
            return DroneRenderer.FPV_MODEL;
        }
        if (item == DroneItems.MOLNIYA_DRONE.get()) {
            return DroneRenderer.MOLNIYA_MODEL;
        }
        if (item == DroneItems.FPV_DRONE.get()) {
            return DroneRenderer.FPV_QUAD_MODEL;
        }
        return null;
    }

    private ItemRenderers() {
    }
}
