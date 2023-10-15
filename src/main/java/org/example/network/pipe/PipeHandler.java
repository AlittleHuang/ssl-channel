package org.example.network.pipe;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface PipeHandler {

    ByteBuffer END_OF_STREAM = ByteBuffer.wrap(new byte[0]).asReadOnlyBuffer();

    default void init(PipeContext ctx) {
    }

    default void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        ctx.fireReceive(buf);
    }

    default void onConnected(PipeContext ctx) throws IOException {
        ctx.fireConnected();
    }

    default void onWrite(PipeContext ctx, ByteBuffer buf) throws IOException {
        ctx.fireWrite(buf);
    }

    default void onClose(PipeContext ctx) throws IOException {
        ctx.fireClose();
    }

    default void onError(PipeContext ctx, Throwable throwable) {
        ctx.fireError(throwable);
    }
}
