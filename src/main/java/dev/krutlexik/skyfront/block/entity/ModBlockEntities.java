package dev.krutlexik.skyfront.block.entity;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, SkyfrontMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShahedTerminalBlockEntity>> SHAHED_TERMINAL =
            BLOCK_ENTITIES.register("shahed_terminal", () ->
                    BlockEntityType.Builder.of(ShahedTerminalBlockEntity::new, ModBlocks.SHAHED_TERMINAL.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
