package org.example.network.channel.handler;

import org.example.network.channel.EventLoopExecutor;

import java.io.IOException;
import java.nio.channels.SelectableChannel;

public interface SelectionKeyHandler extends SelectionKeyHandlerFunction {

    default void init(EventLoopExecutor executor) throws IOException {

    }

    SelectableChannel channel();

    int registerOps();


}
