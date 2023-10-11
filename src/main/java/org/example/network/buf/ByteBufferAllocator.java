package org.example.network.buf;

import java.nio.ByteBuffer;

public interface ByteBufferAllocator extends AutoCloseable {

    ByteBuffer allocate(int capacity);

    void free(ByteBuffer buffer);


}
