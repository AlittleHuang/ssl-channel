package org.example.network.buf;

import org.example.log.Logs;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.TRACE;


public class CachedByteBufferAllocator implements ByteBufferAllocator, AutoCloseable {

    private static final Logger logger = Logs.getLogger(CachedByteBufferAllocator.class);
    public static final CachedByteBufferAllocator HEAP = new CachedByteBufferAllocator(new HeapByteBufferAllocator());

    private final Map<Integer, Cache> cacheMap = new ConcurrentHashMap<>();
    private final ByteBufferAllocator target;
    private long expirationInterval = Duration.ofMinutes(10).toMillis();
    private int maxCacheSize = 2048;
    private volatile boolean closed = false;
    private int minCacheCapacity = 256;

    {
        Thread.startVirtualThread(this::clearExpired);
    }


    public CachedByteBufferAllocator(ByteBufferAllocator target) {
        this.target = target;
    }

    public static ByteBufferAllocator globalHeap() {
        return HEAP;
    }

    @Override
    public ByteBuffer allocate(int capacity) {
        return cacheMap.computeIfAbsent(capacity, Cache::new)
                .getOrDefault(target::allocate);
    }

    @Override
    public void free(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        if (buffer.isReadOnly()) {
            target.free(buffer);
            return;
        }
        int capacity = buffer.capacity();
        Cache cache = cacheMap.get(capacity);
        if (cache == null) {
            target.free(buffer);
            return;
        }
        if (!closed && capacity >= minCacheCapacity && cache.size() < maxCacheSize) {
            cache.put(buffer, System.currentTimeMillis() + expirationInterval);
        } else {
            target.free(buffer);
        }
    }

    public long getExpirationInterval() {
        return expirationInterval;
    }

    public void setExpirationInterval(long expirationInterval) {
        this.expirationInterval = expirationInterval;
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    public int getMinCacheCapacity() {
        return minCacheCapacity;
    }

    public void setMinCacheCapacity(int minCacheCapacity) {
        this.minCacheCapacity = minCacheCapacity;
    }

    @Override
    public void close() {
        closed = true;
    }

    private void clearExpired() {
        long nanos = Duration.ofSeconds(10).toNanos();
        while (!closed) {
            try {
                LockSupport.parkNanos(nanos);
                for (Cache value : cacheMap.values()) {
                    value.clearExpired().forEach(target::free);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "clean cache error", e);
            }
        }
    }

    record BufWrap(ByteBuffer buffer, long time) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BufWrap bufWrap = (BufWrap) o;
            return buffer == bufWrap.buffer;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(buffer);
        }
    }

    static class Cache {
        private final Lock lock = new ReentrantLock();
        private final Deque<BufWrap> deque = new LinkedList<>();
        private final Set<BufWrap> buffers = new HashSet<>();
        private final int capacity;
        private int size;

        public Cache(int capacity) {
            this.capacity = capacity;
        }

        public int size() {
            lock.lock();
            try {
                return size;
            } finally {
                lock.unlock();
            }
        }

        public ByteBuffer getOrDefault(Function<Integer, ByteBuffer> supplier) {
            ByteBuffer result = getInLock(() -> {
                BufWrap mark = deque.pollFirst();
                if (mark != null) {
                    ByteBuffer buffer = mark.buffer;
                    buffers.remove(mark);
                    size--;
                    return buffer.clear();
                }
                return null;
            });
            ByteBuffer buf = result == null ? supplier.apply(capacity) : result;
            logger.log(TRACE, () -> "get: " + ByteBufferUtil.identity(buf));
            return buf;
        }

        public void put(ByteBuffer buffer, long expTime) {
            logger.log(TRACE, () -> "put: " + ByteBufferUtil.identity(buffer));
            runInLock(() -> {
                BufWrap wrap = new BufWrap(buffer, expTime);
                if (buffers.add(wrap)) {
                    deque.addFirst(wrap);
                    size++;
                } else {
                    logger.log(Level.WARNING, "buffer is already in cache");
                }
            });
        }

        private List<ByteBuffer> clearExpired() {
            return getInLock(this::doClearExpired);
        }

        private List<ByteBuffer> doClearExpired() {
            List<ByteBuffer> list = new ArrayList<>();
            long now = System.currentTimeMillis();
            while (true) {
                BufWrap last = deque.peekLast();
                if (last != null && now > last.time()) {
                    try {
                        BufWrap mark = deque.removeLast();
                        if (mark != null) {
                            list.add(mark.buffer());
                            buffers.remove(mark);
                        }
                        size--;
                    } catch (NoSuchElementException e) {
                        break;
                    }
                } else {
                    break;
                }
            }
            logger.log(TRACE, () -> "capacity: " + capacity + ", cacache-size: " + size);

            return list;
        }


        private void runInLock(Runnable runnable) {
            lock.lock();
            try {
                runnable.run();
            } finally {
                lock.unlock();
            }
        }

        private <T> T getInLock(Supplier<T> supplier) {
            lock.lock();
            try {
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }

    }


}
