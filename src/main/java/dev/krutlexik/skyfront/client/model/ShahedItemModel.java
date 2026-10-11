package dev.krutlexik.skyfront.client.model;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.item.ShahedItem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

/** Reuses the drone's entity geo model/texture so the item renders as the real 3D airframe. */
@OnlyIn(Dist.CLIENT)
public class ShahedItemModel extends GeoModel<ShahedItem> {
    @Override
    public ResourceLocation getModelResource(ShahedItem item) {
        return ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "geo/shahed.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ShahedItem item) {
        return ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "textures/entity/shahed.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShahedItem item) {
        return ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "animations/shahed.animation.json");
    }
}
