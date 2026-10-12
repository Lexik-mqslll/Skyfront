package dev.krutlexik.skyfront.block;

import dev.krutlexik.skyfront.block.entity.ShahedTerminalBlockEntity;
import dev.krutlexik.skyfront.item.ShahedItem;
import dev.krutlexik.skyfront.network.NetworkHandler;
import dev.krutlexik.skyfront.network.TerminalDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.UUID;

/** The Shahed launch terminal. */
public class ShahedTerminalBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public ShahedTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Drawn by its block entity renderer (see ShahedTerminalRenderer) from its own model: the laptop on top
     * stands open at an angle a JSON block model cannot hold.
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShahedTerminalBlockEntity(pos, state);
    }

    /**
     * 1.20.1 has a single use(): the held item gets the first say (binding a Shahed), and only when it passes
     * does an empty-handed use with the main hand open the terminal, as 1.21's useItemOn / useWithoutItem do.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        InteractionResult result = this.useItemOn(player.getItemInHand(hand), state, level, pos, player, hand, hit);
        if (result != InteractionResult.PASS || hand != InteractionHand.MAIN_HAND) {
            return result;
        }
        return this.useWithoutItem(state, level, pos, player, hit);
    }

    private InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                        Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ShahedItem)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ShahedTerminalBlockEntity terminal)) {
            return InteractionResult.FAIL;
        }

        CompoundTag tag = ShahedItem.getData(stack);
        UUID uuid = tag.hasUUID(ShahedItem.TAG_UUID) ? tag.getUUID(ShahedItem.TAG_UUID) : UUID.randomUUID();

        if (terminal.findByUuid(uuid) != null) {
            player.displayClientMessage(Component.translatable("msg.sfw.bind.already_this").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }

        if (tag.contains(ShahedItem.TAG_TERMINAL)) {
            int[] p = tag.getIntArray(ShahedItem.TAG_TERMINAL);
            if (p.length == 3) {
                BlockPos other = new BlockPos(p[0], p[1], p[2]);
                if (!other.equals(pos) && this.isBindingStillValid(level, tag, other, uuid)) {
                    player.displayClientMessage(Component.translatable("msg.sfw.bind.already_other",
                            other.getX(), other.getY(), other.getZ()).withStyle(ChatFormatting.RED), true);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        ShahedTerminalBlockEntity.Entry entry = terminal.bind(uuid);
        float health = tag.contains(ShahedItem.TAG_HEALTH) ? tag.getFloat(ShahedItem.TAG_HEALTH) : -1.0f;
        ShahedItem.writeData(stack, uuid, health, pos, level.dimension().location().toString());
        player.displayClientMessage(Component.translatable("msg.sfw.bind.success", entry.number).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    /** True if the old binding marker still points at a live terminal that really contains this drone. */
    private boolean isBindingStillValid(Level level, CompoundTag tag, BlockPos other, UUID uuid) {
        String dim = tag.getString(ShahedItem.TAG_TERMINAL_DIM);
        if (!dim.isEmpty() && !dim.equals(level.dimension().location().toString())) {
            return true;
        }
        if (!level.isLoaded(other)) {
            return true;
        }
        return level.getBlockEntity(other) instanceof ShahedTerminalBlockEntity otherTerminal
                && otherTerminal.findByUuid(uuid) != null;
    }

    private InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof ShahedTerminalBlockEntity terminal) {
            NetworkHandler.sendToPlayer(serverPlayer, TerminalDataPacket.build(serverLevel, pos, terminal, true));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
