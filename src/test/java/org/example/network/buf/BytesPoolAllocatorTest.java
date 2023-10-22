package org.example.network.buf;

import java.nio.ByteBuffer;
import java.time.Duration;

import static org.example.network.buf.PooledAllocator.GLOBAL;

class BytesPoolAllocatorTest {


    public static void main(String[] args) throws InterruptedException {

        ByteBuffer buffer = GLOBAL.allocate(Bytes.DEF_CAP);

        GLOBAL.free(buffer);
        GLOBAL.free(buffer);
        ByteBuffer buf1 = GLOBAL.allocate(Bytes.DEF_CAP);
        GLOBAL.free(buf1);

        GLOBAL.setExpiration(1200);
        Thread.sleep(3000);

    }

}