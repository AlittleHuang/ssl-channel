package org.example.network;

import java.nio.ByteBuffer;

public interface BufferAllocator {

    ByteBuffer allocate(int capacity);

    void free(ByteBuffer buffer);


}
