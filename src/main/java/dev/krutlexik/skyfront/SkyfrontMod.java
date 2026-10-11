package dev.krutlexik.skyfront;

import dev.krutlexik.skyfront.block.ModBlocks;
import dev.krutlexik.skyfront.command.SkyfrontCommand;
import dev.krutlexik.skyfront.config.SkyfrontConfig;
import dev.krutlexik.skyfront.drone.DroneMod;
import dev.krutlexik.skyfront.entity.ModEntities;
import dev.krutlexik.skyfront.item.ModCreativeTabs;
import dev.krutlexik.skyfront.item.ModItems;
import dev.krutlexik.skyfront.network.NetworkHandler;
import dev.krutlexik.skyfront.particle.ModParticles;
import dev.krutlexik.skyfront.sound.ModSounds;
import dev.krutlexik.skyfront.worldmap.WorldMapScanner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModContainer;

@Mod("sfw")
public class SkyfrontMod {
    public static final String MOD_ID = "sfw";

    public SkyfrontMod(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.register(modEventBus);
        ModSounds.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        dev.krutlexik.skyfront.block.entity.ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);
        DroneMod.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(NetworkHandler::register);
        modContainer.registerConfig(ModConfig.Type.COMMON, SkyfrontConfig.SPEC, SkyfrontConfig.FILE_NAME);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SkyfrontCommand.register(event.getDispatcher());
    }
    
    /** Safety net for in-flight drones. */
    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        dev.krutlexik.skyfront.drone.DroneLongRange.serverTick(event.getServer());
        dev.krutlexik.skyfront.drone.DroneLinks.serverTick(event.getServer());
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (dev.krutlexik.skyfront.entity.ShahedEntity drone :
                    level.getEntities(dev.krutlexik.skyfront.entity.ModEntities.SHAHED.get(),
                            d -> d.isAlive() && !d.isParked())) {
                dev.krutlexik.skyfront.util.ChunkLoadHelper.keepTicking(level, drone.chunkPosition());
            }
            for (dev.krutlexik.skyfront.drone.entity.DroneEntity camera :
                    level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(
                                    dev.krutlexik.skyfront.drone.entity.DroneEntity.class),
                            d -> d.isAlive() && !d.isGrounded())) {
                dev.krutlexik.skyfront.util.ChunkLoadHelper.keepTicking(level, camera.chunkPosition());
            }
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            WorldMapScanner.getInstance().startScan(overworld);
        }
    }
}
