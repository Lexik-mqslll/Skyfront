package dev.krutlexik.skyfront.port;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The two ways NeoForge 1.21's {@code StreamCodec} is built in this mod — {@link #of} and
 * {@link #ofMember} — over 1.20.1's {@link FriendlyByteBuf}, so the packet classes shared with the
 * NeoForge build need no more than their imports changed.
 */
public interface StreamCodec<B extends FriendlyByteBuf, V> {

    void encode(B buf, V value);

    V decode(B buf);

    @FunctionalInterface
    interface Encoder<B, V> {
        void encode(B buf, V value);
    }

    @FunctionalInterface
    interface MemberEncoder<V, B> {
        void encode(V value, B buf);
    }

    @FunctionalInterface
    interface Decoder<B, V> {
        V decode(B buf);
    }

    static <B extends FriendlyByteBuf, V> StreamCodec<B, V> of(Encoder<B, V> encoder, Decoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public void encode(B buf, V value) {
                encoder.encode(buf, value);
            }

            @Override
            public V decode(B buf) {
                return decoder.decode(buf);
            }
        };
    }

    static <B extends FriendlyByteBuf, V> StreamCodec<B, V> ofMember(MemberEncoder<V, B> encoder, Decoder<B, V> decoder) {
        return of((buf, value) -> encoder.encode(value, buf), decoder);
    }
}
