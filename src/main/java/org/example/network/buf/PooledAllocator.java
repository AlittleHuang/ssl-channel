package org.example.network.buf;

import org.example.log.Logs;
import org.jetbrains.annotations.NotNull;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

public class PooledAllocator implements ByteBufferAllocator, AutoCloseable {

    private static final Logger logger = Logs.getLogger(PooledAllocator.class);

    public static final PooledAllocator GLOBAL = new PooledAllocator();

    private static final Class<?> HEAP_BYTE_BUFFER_TYPE = ByteBuffer.allocate(0).getClass();

    private final Map<Integer, BytesPool> pools = new ConcurrentHashMap<>();

    private boolean closed;

    private int maxPoolSize = Bytes.K;

    private long expiration = Duration.ofMinutes(1).toMillis();

    private Duration clearPeriod = Duration.ofSeconds(10);


    {
        Thread.startVirtualThread(this::clearExpired);
    }

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


    private void clearExpired() {
        long nanos = clearPeriod.toNanos();
        while (!closed) {
            try {
                LockSupport.parkNanos(nanos);
                for (BytesPool value : pools.values()) {
                    value.cleanExpired();
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "clean cache error", e);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
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

    public void setClearPeriod(Duration clearPeriod) {
        this.clearPeriod = Objects.requireNonNull(clearPeriod);
    }
}
