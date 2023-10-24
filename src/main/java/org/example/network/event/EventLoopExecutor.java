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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;

public class EventLoopExecutor implements AutoCloseable {

    private static final Logger logger = Logs.getLogger(EventLoopExecutor.class);

    private volatile static EventLoopExecutor DEFAULT;


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

    private EventLoopExecutor(Selector selector, ExecutorService executor) {
        this.selector = selector;
        this.executorService = executor;
    }


    public static EventLoopExecutor getDefault() throws IOException {
        if (DEFAULT == null) {
            synchronized (EventLoopExecutor.class) {
                if (DEFAULT == null) {
                    DEFAULT = open();
                }
            }
        }
        return DEFAULT;
    }

    private static EventLoopExecutor open() throws IOException {
        return open(Selector.open());
    }

    public static EventLoopExecutor open(Selector selector) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        return new EventLoopExecutor(selector, executor).start();
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

    public EventLoopExecutor start() {
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
                int select = selector.select(this::handlerSelectKey);
                if (select == 0) {
                    emptyLoopCount++;
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
        logger.log(WARNING, "rebuildSelector");
        for (SelectionKey key : selector.keys()) {
            SelectableChannel channel = key.channel();
            if (channel instanceof SocketChannel) {
                channel.close();
                key.cancel();
            }
        }
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

    public Selector getSelector() {
        return selector;
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

}
