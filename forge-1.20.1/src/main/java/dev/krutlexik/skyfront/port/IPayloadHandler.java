package dev.krutlexik.skyfront.port;

/** Handles one received payload. */
@FunctionalInterface
public interface IPayloadHandler<T extends CustomPacketPayload> {

    void handle(T payload, IPayloadContext context);
}
