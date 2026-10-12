package dev.krutlexik.skyfront.client;

import dev.krutlexik.skyfront.network.ClientPacketHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "sfw", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
class ClientForgeEvents {
    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPacketHandler.clearAll();
    }
}
