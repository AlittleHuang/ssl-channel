package org.example.network.channel.handler;

import org.example.network.channel.SelectorService;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface SelectorKeyHandler {

    void handler(SelectionKey key) throws IOException;


    default void init(SelectorService service) throws IOException {

    }
}
