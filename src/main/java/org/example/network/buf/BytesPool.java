package org.example.network.buf;

import org.example.log.Logs;
import org.example.network.buf.ExpiredCacheCleaner.Clearable;

import java.lang.System.Logger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.*;

public class BytesPool implements Clearable {


    public static final Logger logger = Logs.getLogger(BytesPool.class);
    private final int length;
    private final Set<byte[]> pool = new HashSet<>();
    private final Deque<Long> times = new ArrayDeque<>();
    private final Lock lock = new ReentrantLock();

    private int maxPoolSize = Bytes.K;
    private long expiration = Duration.ofMinutes(1).toMillis();

    public BytesPool(int length) {
        this.length = length;
    }

    public byte[] required() {
        lock.lock();
        try {
            Iterator<byte[]> it = pool.iterator();
            if (it.hasNext()) {
                byte[] next = it.next();
                it.remove();
                times.removeLast();
                logger.log(ALL, () -> "get: " + identity(next) + ", pool size: " + pool.size());
                return next;
            }
        } finally {
            lock.unlock();
        }
        byte[] bytes = new byte[length];
        logger.log(TRACE, () -> "new " + identity(bytes));
        return bytes;
    }

    public void pooled(byte[] bytes) {
        if (bytes == null) {
            return;
        }
        if (bytes.length != length) {
            logger.log(WARNING, "size not matched");
            return;
        }
        lock.lock();
        try {
            if (pool.size() < maxPoolSize) {
                if (pool.add(bytes)) {
                    times.addLast(System.currentTimeMillis());
                    logger.log(ALL, () -> "pooled " + identity(bytes) + ", size: " + pool.size());
                    if (pool.size() == 1) {
                        ExpiredCacheCleaner.getInstance().register(this);
                    }
                } else {
                    logger.log(WARNING, () -> identity(bytes) + "bytes is already in pool");
                }
            } else {
                logger.log(DEBUG, () -> "pool is full, size: " + pool.size());
            }
        } finally {
            lock.unlock();
        }
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        if (expiration <= 0) {
            throw new IllegalArgumentException();
        }
        this.expiration = expiration;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException();
        }
        this.maxPoolSize = maxPoolSize;
    }

    static String identity(byte[] required) {
        return "[" + required.length + "]bytes@"
               + Integer.toString(System.identityHashCode(required), 16);
    }


    @Override
    public boolean clear() {
        lock.lock();
        try {
            int oldSize = pool.size();
            long earliest = System.currentTimeMillis() - expiration;
            Iterator<byte[]> it = pool.iterator();
            while (true) {
                Long time = times.peekFirst();
                if (time != null && time < earliest) {
                    times.removeFirst();
                    if (it.hasNext()) {
                        byte[] bytes = it.next();
                        it.remove();
                        logger.log(ALL, () -> "clear " + identity(bytes));
                    }
                } else {
                    break;
                }
            }
            logger.log(ALL, () -> "pool size: " + oldSize + " -> " + pool.size());
            return pool.isEmpty();
        } finally {
            lock.unlock();
        }
    }
}
