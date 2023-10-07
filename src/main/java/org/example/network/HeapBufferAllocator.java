package org.example.network;

import java.nio.ByteBuffer;

public class HeapBufferAllocator implements ByteBufferAllocator {
    @Override
    public ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocate(capacity);
    }

    @Override
    public void free(ByteBuffer buffer) {

    }
}
