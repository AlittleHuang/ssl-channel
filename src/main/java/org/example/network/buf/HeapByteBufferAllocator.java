package org.example.network.buf;

import java.nio.ByteBuffer;

public class HeapByteBufferAllocator implements ByteBufferAllocator {

    @Override
    public ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocate(capacity);
    }

    @Override
    public void free(ByteBuffer buffer) {

    }

}
