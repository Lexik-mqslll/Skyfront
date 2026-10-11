package dev.krutlexik.skyfront.client.model;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.entity.ShahedEntity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class ShahedModel extends GeoModel<ShahedEntity> {
    @Override
    public ResourceLocation getModelResource(ShahedEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "geo/shahed.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ShahedEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "textures/entity/shahed.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShahedEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(SkyfrontMod.MOD_ID, "animations/shahed.animation.json");
    }
}
