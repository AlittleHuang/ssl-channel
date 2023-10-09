package org.example.network.channel.handler;

import java.nio.channels.SelectableChannel;

public interface ChannelHandler extends SelectorKeyHandler {

    SelectableChannel channel();

    int registerOps();


}
