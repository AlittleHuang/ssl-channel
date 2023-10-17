package org.example.network.event;

import org.example.log.Logs;

import java.io.IOException;
import java.lang.System.Logger;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
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
    private final Thread thread = new Thread(this::work);
    private final ExecutorService executorService;

    public EventLoopExecutor(Selector selector, ExecutorService executor) {
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
                doWork();
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

    private void doWork() throws IOException {
        if (getStatus() == STATUS_RUNNING) {
            selector.select();
        }
        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey key = it.next();
            it.remove();
            if (!key.isValid()) {
                continue;
            }
            if (key.attachment() instanceof SelectionKeyHandlerTask task) {
                task.wakeup(key);
            } else {
                logger.log(WARNING, () -> key + " miss handler");
                key.channel().close();
            }
            if (getStatus() == STATUS_CLOSE) {
                key.cancel();
            }
        }
    }

    public Selector getSelector() {
        return selector;
    }

    @Override
    public void close() {
        if (status.compareAndSet(STATUS_RUNNING, STATUS_CLOSE)) {
            selector.wakeup();
        } else {
            logger.log(WARNING, () -> "error status " + getStatus());
        }
    }

}
