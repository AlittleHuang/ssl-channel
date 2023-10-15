package org.example.network.event;

import java.io.IOException;
import java.nio.channels.SelectionKey;

@FunctionalInterface
public interface SelectionKeyHandlerFunction {

    void handler(SelectionKey key) throws IOException;


}
