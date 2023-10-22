package org.example.network.buf;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PooledAllocator implements ByteBufferAllocator {

    public static final PooledAllocator GLOBAL = new PooledAllocator();

    private static final Class<?> HEAP_BYTE_BUFFER_TYPE = ByteBuffer.allocate(0).getClass();

    private final Map<Integer, BytesPool> pools = new ConcurrentHashMap<>();

    private int maxPoolSize = Bytes.K;

    private long expiration = Duration.ofMinutes(1).toMillis();

    @Override
    public ByteBuffer allocate(int capacity) {
        byte[] bytes = pools.computeIfAbsent(capacity, this::newBytesPool).required();
        return ByteBuffer.wrap(bytes).clear();
    }

    @NotNull
    private BytesPool newBytesPool(Integer length) {
        BytesPool pool = new BytesPool(length);
        pool.setExpiration(expiration);
        pool.setMaxPoolSize(maxPoolSize);
        return pool;
    }

    @Override
    public void free(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        if (buffer.getClass() == HEAP_BYTE_BUFFER_TYPE) {
            byte[] bytes = buffer.array();
            BytesPool pool = pools.get(bytes.length);
            if (pool != null) {
                pool.pooled(bytes);
            }
        }
    }


    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        for (BytesPool value : pools.values()) {
            value.setMaxPoolSize(maxPoolSize);
        }
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
        for (BytesPool value : pools.values()) {
            value.setExpiration(expiration);
        }
    }

}
