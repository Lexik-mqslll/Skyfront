package dev.krutlexik.skyfront.port;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 1.20.1 has no registry-aware buffer: {@link FriendlyByteBuf} itself writes items and the rest. This
 * is that buffer under the name the packets shared with the NeoForge build are written against.
 */
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {

    public RegistryFriendlyByteBuf(ByteBuf source) {
        super(source);
    }
}
