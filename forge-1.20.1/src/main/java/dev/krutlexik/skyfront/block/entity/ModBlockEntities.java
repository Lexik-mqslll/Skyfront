package dev.krutlexik.skyfront.block.entity;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SkyfrontMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<ShahedTerminalBlockEntity>> SHAHED_TERMINAL =
            BLOCK_ENTITIES.register("shahed_terminal", () ->
                    BlockEntityType.Builder.of(ShahedTerminalBlockEntity::new, ModBlocks.SHAHED_TERMINAL.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
