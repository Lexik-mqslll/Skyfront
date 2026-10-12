package dev.krutlexik.skyfront.drone.item;

import dev.krutlexik.skyfront.drone.entity.DroneEntity;
import dev.krutlexik.skyfront.drone.registry.DroneEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Right-click a block to deploy the drone hovering just above it. */
public class DroneItem extends Item {
    private final Supplier<? extends EntityType<? extends DroneEntity>> type;
    /** Launched only from a rail: it cannot be set down on the ground at all (see the launcher). */
    private final boolean railOnly;

    /** The recon drone, for the plain {@code DroneItem::new} the registry hands a bare Properties to. */
    public DroneItem(Properties properties) {
        this(properties, DroneEntities.DRONE);
    }

    public DroneItem(Properties properties, Supplier<? extends EntityType<? extends DroneEntity>> type) {
        this(properties, type, false);
    }

    public DroneItem(Properties properties, Supplier<? extends EntityType<? extends DroneEntity>> type,
                     boolean railOnly) {
        super(properties);
        this.type = type;
        this.railOnly = railOnly;
    }

    /** Drawn with the drone's own model (see ItemRenderers). */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return dev.krutlexik.skyfront.client.ItemRenderers.forItem(DroneItem.this);
            }
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (this.railOnly) {
            if (!level.isClientSide && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.translatable("msg.sfw.place.launcher_only")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        DroneEntity drone = this.type.get().create(level);
        if (drone == null) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        drone.setYRot(player != null ? player.getYRot() : 0.0F);
        double floor = floorUnder(level, pos);
        if (drone instanceof dev.krutlexik.skyfront.drone.entity.FpvDroneEntity) {
            var stand = dev.krutlexik.skyfront.drone.registry.DroneEntities.DRONE_STAND.get().create(level);
            if (stand != null) {
                stand.setPos(pos.getX() + 0.5D, floor, pos.getZ() + 0.5D);
                stand.holding(drone);
                serverLevel.addFreshEntity(stand);
            }
            drone.setPos(pos.getX() + 0.5D, floor + dev.krutlexik.skyfront.drone.entity.FpvDroneEntity.STAND_HEIGHT,
                    pos.getZ() + 0.5D);
        } else {
            drone.setPos(pos.getX() + 0.5D, floor, pos.getZ() + 0.5D);
        }
        serverLevel.addFreshEntity(drone);

        ItemStack stack = context.getItemInHand();
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * The height of whatever the drone will actually stand on in this block space: the top of a slab, a snow
     * layer or a carpet, not the bottom of the block above it, which left a drone put down on a slab hanging
     * half a block over it.
     */
    private static double floorUnder(Level level, BlockPos pos) {
        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(pos.getX() + 0.5D, pos.getY() + 0.999D, pos.getZ() + 0.5D);
        net.minecraft.world.phys.Vec3 to = from.subtract(0.0D, 1.999D, 0.0D);
        var hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                (net.minecraft.world.entity.Entity) null));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? pos.getY() : hit.getLocation().y;
    }
}
