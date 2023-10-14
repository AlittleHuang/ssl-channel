package org.example.network.channel.handler;

import org.example.network.channel.EventLoopExecutor;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

public class SelectionKeyHandlerImpl implements SelectionKeyHandler {

    private final SelectionKeyHandlerFunctiom handler;
    private final Consumer<EventLoopExecutor> initializer;
    private final SelectableChannel channel;
    private final int registerOps;

    public SelectionKeyHandlerImpl(SelectionKeyHandlerFunctiom handler,
                                   SelectableChannel channel,
                                   int registerOps) {
        this.handler = handler;
        this.channel = channel;
        this.registerOps = registerOps;
        this.initializer = null;
    }

    public SelectionKeyHandlerImpl(SelectionKeyHandlerFunctiom handler,
                                   Consumer<EventLoopExecutor> initializer,
                                   SelectableChannel channel,
                                   int registerOps) {
        this.handler = handler;
        this.initializer = initializer;
        this.channel = channel;
        this.registerOps = registerOps;
    }

    @Override

    public void handler(SelectionKey key) throws IOException {
        handler.handler(key);
    }

    @Override
    public void init(EventLoopExecutor executor) {
        if (initializer != null) {
            initializer.accept(executor);
        }
    }

    @Override
    public SelectableChannel channel() {
        return channel;
    }

    @Override
    public int registerOps() {
        return registerOps;
    }


}
