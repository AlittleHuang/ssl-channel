package org.example.network;

import java.nio.ByteBuffer;

public interface ByteBufferAllocator {

    ByteBuffer allocate(int capacity);

    void free(ByteBuffer buffer);


}
