package org.example.network.channel.pipe;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface PipeHandler {

    default void init(PipeContext ctx) {
    }

    default void onReceive(PipeContext context, ByteBuffer buf) {
        context.fireReceive(buf);
    }

    default void onConnected(PipeContext ctx) throws IOException {
        ctx.fireConnected();
    }

    default void onWrite(PipeContext context, ByteBuffer buf) throws IOException {
        context.fireWrite(buf);
    }

    static PipeHandler readHandler(PipeReadHandler handler) {
        return handler;
    }

    static PipeHandler writeHandler(PipeWriteHandler handler) {
        return handler;
    }

    static PipeHandler initHandler(PipeInitHandler handler) {
        return handler;
    }

    static PipeHandler connectedHandler(PipeConnectedHandler handler) {
        return handler;
    }

}
