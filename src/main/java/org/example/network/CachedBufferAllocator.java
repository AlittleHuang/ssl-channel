package org.example.network;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;


public class CachedBufferAllocator implements BufferAllocator, AutoCloseable {

    private static final Logger logger = Logger.getLogger(CachedBufferAllocator.class.getName());

    private final Map<Integer, Cache> cacheMap = new ConcurrentHashMap<>();
    private final BufferAllocator target;
    private long expirationInterval = Duration.ofMinutes(10).toMillis();
    private int maxCacheSize = 2048;


    public CachedBufferAllocator(BufferAllocator target) {
        this.target = target;
    }

    public static BufferAllocator heap() {
        return new CachedBufferAllocator(new HeapBufferAllocator());
    }

    @Override
    public ByteBuffer allocate(int capacity) {
        Cache cache = cacheMap.get(capacity);
        return cache == null ? target.allocate(capacity) : cache.getOrDefault(() -> target.allocate(capacity));
    }

    @Override
    public void free(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        int capacity = buffer.capacity();
        Cache cache = cacheMap.computeIfAbsent(capacity, k -> new Cache());
        if (!cache.closed && cache.size() < maxCacheSize) {
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

    private TimeMark wrap(ByteBuffer buffer) {
        return new TimeMark(buffer, System.currentTimeMillis() + expirationInterval);
    }

    @Override
    public void close() {
        for (Cache value : cacheMap.values()) {
            value.closed = true;
        }
    }

    static class TimeMark {
        private final ByteBuffer buffer;
        private final long time;

        TimeMark(ByteBuffer buffer, long time) {
            this.buffer = buffer;
            this.time = time;
        }
    }

    static class Cache {
        private final Lock lock = new ReentrantLock();
        private final Deque<TimeMark> deque = new LinkedList<>();
        private final Set<ByteBuffer> buffers = new HashSet<>();
        private int size;
        private volatile boolean closed = false;

        {
            Thread.startVirtualThread(this::cleanExpirationElement);
        }

        public int size() {
            lock.lock();
            try {
                return size;
            } finally {
                lock.unlock();
            }
        }

        public ByteBuffer getOrDefault(Supplier<ByteBuffer> supplier) {
            ByteBuffer result = getInLock(() -> {
                TimeMark mark = deque.pollFirst();
                if (mark != null) {
                    ByteBuffer buffer = mark.buffer;
                    buffers.remove(buffer);
                    size--;
                    return buffer.clear();
                }
                return null;
            });
            return result == null ? supplier.get() : result;
        }

        public void put(ByteBuffer buffer, long expTime) {
            runInLock(() -> {
                if (buffers.add(buffer)) {
                    deque.addFirst(new TimeMark(buffer, expTime));
                    size++;
                } else {
                    logger.warning("buffer is already in cache");
                }
            });
        }


        private void cleanExpirationElement() {
            long nanos = Duration.ofSeconds(1).toNanos();
            while (!closed) {
                try {
                    LockSupport.parkNanos(nanos);
                    runInLock(this::doClean);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "clean cache error", e);
                }
            }
            runInLock(this::clear);
        }

        private void clear() {
            deque.clear();
            buffers.clear();
        }

        private void doClean() {
            long now = System.currentTimeMillis();
            while (true) {
                TimeMark last = deque.peekLast();
                if (last != null && last.time > now) {
                    try {
                        TimeMark mark = deque.removeLast();
                        if (mark != null) {
                            buffers.remove(mark.buffer);
                        }
                        size--;
                    } catch (NoSuchElementException e) {
                        break;
                    }
                } else {
                    break;
                }
            }
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
