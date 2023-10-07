package org.example.network;

import java.nio.ByteBuffer;

public interface ByteBufferAllocator extends AutoCloseable {

    ByteBuffer allocate(int capacity);

    void free(ByteBuffer buffer);


}
