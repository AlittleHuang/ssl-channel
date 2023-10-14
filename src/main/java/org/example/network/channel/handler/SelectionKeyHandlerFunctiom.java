package org.example.network.channel.handler;

import java.io.IOException;
import java.nio.channels.SelectionKey;

@FunctionalInterface
public interface SelectionKeyHandlerFunctiom {

    void handler(SelectionKey key) throws IOException;


}
