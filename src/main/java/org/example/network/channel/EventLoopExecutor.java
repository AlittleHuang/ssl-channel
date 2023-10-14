package org.example.network.channel;

import org.example.network.buf.ByteBufferAllocator;
import org.example.network.buf.CachedByteBufferAllocator;
import org.example.network.channel.handler.SelectionKeyHandlerFunctiom;
import org.example.network.channel.handler.SelectionKeyHandler;
import org.example.network.channel.handler.SelectionKeyHandlerImpl;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

public class EventLoopExecutor implements AutoCloseable {

    private static final Logger logger = Logger
            .getLogger(EventLoopExecutor.class.getName());


    public static int STATUS_READY = 0;
    public static int STATUS_RUNNING = 1;
    public static int STATUS_CLOSE = 0;

    private final Selector selector;
    private final ByteBufferAllocator allocator;
    private final AtomicInteger status = new AtomicInteger();
    private final Thread thread = new Thread(this::work);

    public EventLoopExecutor(Selector selector) {
        this.selector = selector;
        this.allocator = CachedByteBufferAllocator.HEAP;
    }

    public static EventLoopExecutor start(Selector selector) {
        EventLoopExecutor service = new EventLoopExecutor(selector);
        service.start();
        return service;
    }

    public void register(SelectionKeyHandler handler) throws IOException {
        checkRunning();
        handler.init(this);
        registerAnsWakeup(handler.channel(), handler.registerOps(), handler);
    }

    public void register(SelectableChannel channel,
                         int ops,
                         SelectionKeyHandlerFunctiom handler)
            throws IOException {
        register(new SelectionKeyHandlerImpl(handler, channel, ops));
    }

    private void registerAnsWakeup(SelectableChannel channel, int ops, SelectionKeyHandlerFunctiom handler)
            throws IOException {
        channel.register(selector, ops, handler);
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

    public void start() {
        if (status.compareAndSet(STATUS_READY, STATUS_RUNNING)) {
            thread.start();
        } else {
            throw new IllegalStateException("error status " + getStatus());
        }
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
            if (key.attachment() instanceof SelectionKeyHandlerFunctiom handler) {
                handler.handler(key);
            } else {
                key.interestOps(0);
                logger.log(WARNING, () -> key + " miss handler");
            }
            if (getStatus() == STATUS_CLOSE) {
                key.cancel();
            }
        }
    }

    public ByteBufferAllocator getAllocator() {
        return allocator;
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
