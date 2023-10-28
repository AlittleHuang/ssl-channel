package org.example.network.tcp;

import org.example.network.pipe.Pipeline;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public interface PipeReadHandler {
    void handler(SocketChannel channel, Pipeline pipeline, SocketAddress remote) throws IOException;

}
