package dev.krutlexik.skyfront.block;

import dev.krutlexik.skyfront.network.NetworkHandler;
import dev.krutlexik.skyfront.network.SirenSoundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class SirenBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    
    public SirenBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
    
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(pos);
            boolean wasPowered = state.getValue(POWERED);
            
            if (powered != wasPowered) {
                level.setBlock(pos, state.setValue(POWERED, powered), 3);
                
                if (powered) {
                    level.scheduleTick(pos, this, 1);
                }
            }
        }
    }
    
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            int range = dev.krutlexik.skyfront.config.SkyfrontConfig.SIREN_RANGE.get();
            NetworkHandler.sendToNear(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, range,
                    new SirenSoundPacket(pos, false, range));
            level.scheduleTick(pos, this, 500);
        }
    }
    
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            if (!level.isClientSide && state.getValue(POWERED)) {
                level.scheduleTick(pos, this, 1);
            }
        }
    }
    
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                NetworkHandler.sendToAllPlayers(new SirenSoundPacket(pos, true));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
