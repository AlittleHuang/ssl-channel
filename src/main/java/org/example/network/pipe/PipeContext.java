package org.example.network.pipe;

import java.io.IOException;
import java.net.InetSocketAddress;
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
        return pipeline().allocate(capacity);
    }


    default void free(ByteBuffer buf) {
        pipeline().free(buf);
    }

    void fireConnected() throws IOException;

    void remove();

    void fireClose() throws IOException;

    void replace(PipeHandler handler);

    void fireError(Throwable throwable);

    void fireConnect(InetSocketAddress address) throws IOException;

    void fireReadeTheEnd() throws IOException;

    long getId();
}

