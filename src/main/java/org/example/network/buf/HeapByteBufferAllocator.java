package org.example.network.buf;

import org.example.network.buf.ByteBufferAllocator;

import java.nio.ByteBuffer;

public class HeapByteBufferAllocator implements ByteBufferAllocator {
    @Override
    public ByteBuffer allocate(int capacity) {
        return ByteBuffer.allocate(capacity);
    }

    @Override
    public void free(ByteBuffer buffer) {

    }

    @Override
    public void close() {

    }
}
