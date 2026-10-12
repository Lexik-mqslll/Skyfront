package dev.krutlexik.skyfront.port;

import net.minecraft.resources.ResourceLocation;

/**
 * Stands in on Forge 1.20.1 for the payload interface NeoForge 1.21's networking is built on, so that
 * every packet class is the same code in both builds bar its imports. The payloads travel here on one
 * Forge {@code SimpleChannel} (see {@link PayloadRegistrar}).
 */
public interface CustomPacketPayload {

    Type<? extends CustomPacketPayload> type();

    /** A payload's name. Kept for parity with the NeoForge build; the channel itself keys on the class. */
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
