package dev.krutlexik.skyfront.client.renderer;

import dev.krutlexik.skyfront.client.model.ShahedItemModel;
import dev.krutlexik.skyfront.item.ShahedItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Renders the Shahed-136 item as its 3D geo model (inventory icon, hand, ground, item frames). */
@OnlyIn(Dist.CLIENT)
public class ShahedItemRenderer extends GeoItemRenderer<ShahedItem> {
    public ShahedItemRenderer() {
        super(new ShahedItemModel());
    }
}
