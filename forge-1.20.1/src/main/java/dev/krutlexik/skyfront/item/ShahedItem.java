package dev.krutlexik.skyfront.item;

import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.entity.ModEntities;
import dev.krutlexik.skyfront.entity.ShahedEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** The Shahed-136 drone as an item. */
public class ShahedItem extends Item implements GeoItem {
    public static final String TAG_UUID = "ShahedUuid";
    public static final String TAG_HEALTH = "ShahedHealth";
    public static final String TAG_TERMINAL = "ShahedTerminal";
    public static final String TAG_TERMINAL_DIM = "ShahedTerminalDim";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ShahedItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Renders the item with the drone's real 3D geo model instead of a flat sprite. */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return dev.krutlexik.skyfront.client.ItemRenderers.forItem(ShahedItem.this);
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * A Shahed is never set down on the ground: it goes on a launch rail (right-click the {@link
     * dev.krutlexik.skyfront.entity.LauncherEntity}, which lays it there) and nowhere else.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!context.getLevel().isClientSide && player != null) {
            player.displayClientMessage(Component.translatable("msg.sfw.place.launcher_only")
                    .withStyle(ChatFormatting.RED), true);
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = getData(stack);
        float max = (float) Math.max(1.0, SkyfrontConfig.SHAHED_HEALTH.get());
        float hp = tag.contains(TAG_HEALTH) && tag.getFloat(TAG_HEALTH) >= 0.0f ? tag.getFloat(TAG_HEALTH) : max;
        ChatFormatting color = hp >= max ? ChatFormatting.GREEN : (hp > max * 0.25f ? ChatFormatting.YELLOW : ChatFormatting.RED);
        tooltip.add(Component.translatable("tooltip.sfw.shahed.health",
                String.format("%.0f", hp), String.format("%.0f", max)).withStyle(color));
        if (tag.contains(TAG_TERMINAL)) {
            int[] p = tag.getIntArray(TAG_TERMINAL);
            if (p.length == 3) {
                tooltip.add(Component.translatable("tooltip.sfw.shahed.bound", p[0], p[1], p[2]).withStyle(ChatFormatting.AQUA));
            }
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    /** Read-only copy of the item's custom data tag. */
    /**
     * Puts the drone identity a stack carries onto a fresh entity: its UUID, its health and the terminal it
     * is bound to.
     */
    public static void applyStackData(ShahedEntity drone, net.minecraft.world.level.Level level, ItemStack stack) {
        CompoundTag tag = getData(stack);
        UUID uuid = tag.hasUUID(TAG_UUID) ? tag.getUUID(TAG_UUID) : UUID.randomUUID();
        if (level instanceof ServerLevel serverLevel && serverLevel.getEntity(uuid) != null) {
            uuid = UUID.randomUUID();
        }
        drone.setUUID(uuid);
        drone.setDroneHealthDirect(tag.contains(TAG_HEALTH) ? tag.getFloat(TAG_HEALTH) : -1.0f);
        if (tag.contains(TAG_TERMINAL)) {
            int[] p = tag.getIntArray(TAG_TERMINAL);
            if (p.length == 3) {
                drone.setBoundTerminal(new BlockPos(p[0], p[1], p[2]));
            }
        }
    }

    public static CompoundTag getData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.copy() : new CompoundTag();
    }

    /** Writes drone identity into the item stack. */
    public static void writeData(ItemStack stack, UUID uuid, float health, @Nullable BlockPos terminal, @Nullable String terminalDim) {
        CompoundTag tag = getData(stack);
        tag.putUUID(TAG_UUID, uuid);
        if (health >= 0.0f) {
            tag.putFloat(TAG_HEALTH, health);
        } else {
            tag.remove(TAG_HEALTH);
        }
        if (terminal != null) {
            tag.putIntArray(TAG_TERMINAL, new int[]{terminal.getX(), terminal.getY(), terminal.getZ()});
            if (terminalDim != null) {
                tag.putString(TAG_TERMINAL_DIM, terminalDim);
            }
        } else {
            tag.remove(TAG_TERMINAL);
            tag.remove(TAG_TERMINAL_DIM);
        }
        stack.setTag(tag);
    }
}
