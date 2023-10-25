package org.example.network.event;

import org.example.log.Logs;
import org.example.network.buf.Bytes;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.System.Logger;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;

public class NioEventLoopExecutor implements AutoCloseable {

    private static final Logger logger = Logs.getLogger(NioEventLoopExecutor.class);
    public static final long WAIT_NANOS = Duration.ofMillis(1).toNanos();
    public static final long SELECT_TIME_OUT = Duration.ofSeconds(10).toMillis();

    private volatile static NioEventLoopExecutor DEFAULT;


    public static int STATUS_READY = 0;
    public static int STATUS_RUNNING = 1;
    public static int STATUS_CLOSE = 2;

    private final Selector selector;
    private final AtomicInteger status = new AtomicInteger();
    private final Thread thread = getWorkThread();

    private int emptyLoopCount;

    @NotNull
    private Thread getWorkThread() {
        Thread thread = new Thread(this::work);
        thread.setDaemon(true);
        return thread;
    }

    private final ExecutorService executorService;

    private NioEventLoopExecutor(Selector selector, ExecutorService executor) {
        this.selector = selector;
        this.executorService = executor;
    }


    public static NioEventLoopExecutor getDefault() throws IOException {
        if (DEFAULT == null) {
            synchronized (NioEventLoopExecutor.class) {
                if (DEFAULT == null) {
                    DEFAULT = open();
                }
            }
        }
        return DEFAULT;
    }

    private static NioEventLoopExecutor open() throws IOException {
        return open(Selector.open());
    }

    public static NioEventLoopExecutor open(Selector selector) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        return new NioEventLoopExecutor(selector, executor).start();
    }

    public void register(SelectionKeyHandler handler) throws IOException {
        checkRunning();
        handler.init(this);
        registerAnsWakeup(handler.channel(), handler.registerOps(), handler);
    }

    public void register(SelectableChannel channel,
                         int ops,
                         SelectionKeyHandlerFunction handler)
            throws IOException {
        register(new SelectionKeyHandlerImpl(handler, channel, ops));
    }

    private void registerAnsWakeup(SelectableChannel channel,
                                   int ops,
                                   SelectionKeyHandlerFunction handler)
            throws IOException {
        SelectionKeyHandlerTask task = new SelectionKeyHandlerTask(executorService, handler);
        channel.register(selector, ops, task);
        selector.wakeup();
    }

    private void checkRunning() {
        if (getStatus() != STATUS_RUNNING) {
            throw new IllegalStateException("error status " + getStatus());
        }
    }

    public int getStatus() {
        return status.intValue();
    }

    public NioEventLoopExecutor start() {
        if (status.compareAndSet(STATUS_READY, STATUS_RUNNING)) {
            thread.start();
        } else {
            throw new IllegalStateException("error status " + getStatus());
        }
        return this;
    }

    private void work() {
        while (getStatus() == STATUS_RUNNING) {
            try {
                int select = selector.select(this::handlerSelectKey, SELECT_TIME_OUT);
                if (select == 0) {
                    emptyLoopCount++;
                    LockSupport.parkNanos(WAIT_NANOS);
                    logger.log(DEBUG, "no keys consumed");
                } else {
                    emptyLoopCount = 0;
                }
                if (emptyLoopCount >= Bytes.M) {
                    rebuildSelector();
                    emptyLoopCount = 0;
                }
            } catch (Exception e) {
                logger.log(WARNING, () -> "handler select error", e);
            }
        }
        try (selector) {
            logger.log(DEBUG, () -> "close " + selector);
        } catch (IOException e) {
            logger.log(WARNING, () -> "close " + selector + " error", e);
        }
    }

    private void rebuildSelector() throws IOException {
        Set<SelectionKey> keys = selector.keys();
        logger.log(WARNING, "rebuildSelector, key size: " + keys.size());
        for (SelectionKey key : keys) {
            SelectableChannel channel = key.channel();
            if (channel instanceof SocketChannel) {
                channel.close();
                key.cancel();
            }
        }
        logger.log(WARNING, "rebuild, key size: " + keys.size());
    }

    private void handlerSelectKey(SelectionKey key) {
        SelectableChannel channel = key.channel();
        try {
            if (key.attachment() instanceof SelectionKeyHandlerTask task) {
                task.handler(key);
            } else {
                logger.log(WARNING, () -> key + " miss handler");
                channel.close();
                key.cancel();
            }
            if (getStatus() == STATUS_CLOSE) {
                key.cancel();
            }
        } catch (Exception e) {
            try (channel) {
                logger.log(WARNING, channel + " handler select key error", e);
            } catch (Exception exception) {
                logger.log(WARNING, channel + " close error", e);
            }
        }
    }

    @Override
    public void close() {
        if (DEFAULT == this) {
            throw new UnsupportedOperationException();
        }
        if (status.compareAndSet(STATUS_RUNNING, STATUS_CLOSE)) {
            selector.wakeup();
        } else {
            logger.log(WARNING, () -> "error status " + getStatus());
        }
    }

    public SelectionKey key(SelectableChannel ch) {
        return ch.keyFor(selector);
    }

}
