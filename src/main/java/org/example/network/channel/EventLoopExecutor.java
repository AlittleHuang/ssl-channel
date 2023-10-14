package org.example.network.channel;

import org.example.concurrent.SingleThreadTask;
import org.example.network.channel.handler.SelectionKeyHandler;
import org.example.network.channel.handler.SelectionKeyHandlerFunction;
import org.example.network.channel.handler.SelectionKeyHandlerImpl;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

public class EventLoopExecutor implements AutoCloseable {

    private static final Logger logger = Logger
            .getLogger(EventLoopExecutor.class.getName());

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
        int nThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
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
        SelectionKey key = channel.register(selector, ops);
        key.attach(new SingleThreadTask(executorService, () -> {
            try {
                handler.handler(key);
            } catch (Exception e) {
                logger.log(WARNING, e, () -> "handler " + key + " error");
            }
        }));
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
        while (!selector.keys().isEmpty() || getStatus() == STATUS_RUNNING) {
            try {
                doWork();
            } catch (Exception e) {
                logger.log(WARNING, e, () -> "handler select error");
            }
        }
    }

    private void doWork() throws IOException {
        selector.select();
        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey key = it.next();
            it.remove();
            if (!key.isValid()) {
                continue;
            }
            if (key.attachment() instanceof SingleThreadTask task) {
                task.wakeup();
            } else {
                key.interestOps(0);
                logger.log(WARNING, () -> key + " miss handler");
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
            logger.warning(() -> "error status " + getStatus());
        }
    }

}
