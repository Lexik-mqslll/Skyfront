package dev.krutlexik.skyfront.item;

import dev.krutlexik.skyfront.entity.LauncherEntity;
import dev.krutlexik.skyfront.entity.ModEntities;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** The launch rail as an item: set down where it is used, its rails rising the way the player faces. */
public class LauncherItem extends Item {
    public LauncherItem(Properties properties) {
        super(properties);
    }

    /** Drawn with the launch rail's own model (see ItemRenderers). */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return dev.krutlexik.skyfront.client.ItemRenderers.forItem(LauncherItem.this);
            }
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Vec3 at = context.getClickedFace() == Direction.UP
                ? context.getClickLocation()
                : Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        LauncherEntity launcher = ModEntities.LAUNCHER.get().create(level);
        if (launcher == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() : 0.0F;
        launcher.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        level.addFreshEntity(launcher);
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
