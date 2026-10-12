package dev.krutlexik.skyfront.block;

import dev.krutlexik.skyfront.SkyfrontMod;
import dev.krutlexik.skyfront.item.ModItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SkyfrontMod.MOD_ID);
    public static final RegistryObject<Block> SHAHED_TERMINAL = registerBlock("shahed_terminal",
            () -> new ShahedTerminalBlock(BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> SHAHED_RADAR = registerBlock("shahed_radar",
            () -> new ShahedRadarBlock(BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> SIREN = registerBlock("siren",
            () -> new SirenBlock(BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, RegistryObject<T> block) {
        ModItems.ITEMS.register(name, () -> new ModBlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

    /**
     * The blocks' items. The terminal's is drawn with the terminal's own model (see ItemRenderers); the
     * others keep their plain block models.
     */
    public static class ModBlockItem extends BlockItem {
        public ModBlockItem(Block block, Item.Properties properties) {
            super(block, properties);
        }

        @Override
        public void initializeClient(Consumer<IClientItemExtensions> consumer) {
            consumer.accept(new IClientItemExtensions() {
                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return dev.krutlexik.skyfront.client.ItemRenderers.forItem(ModBlockItem.this);
                }
            });
        }
    }
}
