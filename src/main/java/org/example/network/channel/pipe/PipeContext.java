package org.example.network.channel.pipe;

import org.example.network.channel.EventLoopExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface PipeContext {

    PipeContext addBefore(PipeHandler handler);

    PipeContext addAfter(PipeHandler handler);

    PipeContext addFirst(PipeHandler handler);

    PipeContext addLast(PipeHandler handler);

    Pipeline pipeline();

    void fireReceive(ByteBuffer buf) throws IOException;

    void fireWrite(ByteBuffer buf) throws IOException;


    default ByteBuffer allocate(int capacity) {
        // noinspection resource
        EventLoopExecutor executor = executor();
        if (executor == null) {
            return ByteBuffer.allocate(capacity);
        }
        return pipeline().allocate(capacity);
    }


    default void free(ByteBuffer buf) {
        // noinspection resource
        EventLoopExecutor executor = executor();
        if (executor != null) {
            pipeline().free(buf);
        }
    }

    void fireConnected() throws IOException;

    void remove();

    void fireClose() throws IOException;

    EventLoopExecutor executor();

    void replace(PipeHandler handler);

    void fireError(Throwable throwable);
}

