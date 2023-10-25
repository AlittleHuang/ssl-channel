package org.example.network.event;

import java.io.IOException;
import java.nio.channels.SelectableChannel;

public interface SelectionKeyHandler extends SelectionKeyHandlerFunction {

    default void init(NioEventLoopExecutor executor) throws IOException {

    }

    SelectableChannel channel();

    int registerOps();


}
