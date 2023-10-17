package org.example.network.buf;

import org.example.log.Logs;

import java.lang.System.Logger;
import java.nio.ByteBuffer;

import static java.lang.System.Logger.Level.TRACE;

public class HeapByteBufferAllocator implements ByteBufferAllocator {

    private static final Logger logger = Logs.getLogger(HeapByteBufferAllocator.class);

    @Override
    public ByteBuffer allocate(int capacity) {
        ByteBuffer buf = ByteBuffer.allocate(capacity);
        logger.log(TRACE, () -> "allocate: " + ByteBufferUtil.identity(buf));
        return buf;
    }

    @Override
    public void free(ByteBuffer buffer) {
        if (buffer.capacity() > 0) {
            logger.log(TRACE, () -> "free:" + ByteBufferUtil.identity(buffer));
        }
    }

}
