package org.example.network.channel.pipe;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface PipeContext {

    PipeContext addBefore(PipeHandler handler);

    PipeContext addAfter(PipeHandler handler);

    PipeContext addFirst(PipeHandler handler);

    PipeContext addLast(PipeHandler handler);

    void fireReceive(ByteBuffer buf);


    void fireWrite(ByteBuffer buf) throws IOException;


    default ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocate(capacity);
    }


    default void free(ByteBuffer buf) {

    }

    void fireConnected() throws IOException;

    void remove();

}

