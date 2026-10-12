package dev.krutlexik.skyfront.port;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Registers the mod's payloads the way NeoForge 1.21's registrar does, on one Forge
 * {@link SimpleChannel}. A client and server whose versions differ are turned away at the door, as on
 * NeoForge.
 *
 * <p>Forge keys a message on its class rather than its name, so each registration names the class.
 * Handlers run on the network thread exactly as NeoForge's do; they hand their work to the main
 * thread themselves with {@link IPayloadContext#enqueueWork}.
 */
public final class PayloadRegistrar {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SimpleChannel channel;
    private int nextId;

    public PayloadRegistrar(ResourceLocation name, String version) {
        this.channel = NetworkRegistry.newSimpleChannel(name, () -> version, version::equals, version::equals);
        PacketDistributor.use(this.channel);
    }

    public <T extends CustomPacketPayload> void playToClient(Class<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            IPayloadHandler<T> handler) {
        register(type, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }

    public <T extends CustomPacketPayload> void playToServer(Class<T> type,
                                                            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                            IPayloadHandler<T> handler) {
        register(type, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }

    private <T extends CustomPacketPayload> void register(Class<T> type,
                                                         StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                         IPayloadHandler<T> handler, NetworkDirection direction) {
        this.channel.messageBuilder(type, this.nextId++, direction)
                .encoder((payload, buf) -> codec.encode(wrap(buf), payload))
                .decoder(buf -> codec.decode(wrap(buf)))
                .consumerNetworkThread((payload, supplier) -> {
                    NetworkEvent.Context context = supplier.get();
                    handler.handle(payload, new Context(context));
                    context.setPacketHandled(true);
                })
                .add();
    }

    private static RegistryFriendlyByteBuf wrap(FriendlyByteBuf buf) {
        return buf instanceof RegistryFriendlyByteBuf registry ? registry : new RegistryFriendlyByteBuf(buf);
    }

    private record Context(NetworkEvent.Context forge) implements IPayloadContext {
        @Override
        @Nullable
        public Player player() {
            ServerPlayer sender = this.forge.getSender();
            if (sender != null) {
                return sender;
            }
            return this.forge.getDirection().getReceptionSide().isClient() ? ClientPayloadAccess.player() : null;
        }

        /**
         * Forge runs the task through a future that swallows whatever it throws; NeoForge logged it.
         * Without the log a packet whose handler fails just quietly does nothing.
         */
        @Override
        public CompletableFuture<Void> enqueueWork(Runnable task) {
            return this.forge.enqueueWork(task).exceptionally(failure -> {
                LOGGER.error("Failed to process a synchronized task of a payload", failure);
                return null;
            });
        }
    }
}
