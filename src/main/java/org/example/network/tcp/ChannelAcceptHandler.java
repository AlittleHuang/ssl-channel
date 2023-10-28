package org.example.network.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CompletableFuture;

public interface ChannelAcceptHandler {

    CompletableFuture<Void> handler(ServerSocketChannel channel,
                              PipeReadHandler reader,
                              PipeConfig config,
                              InetSocketAddress bind) throws IOException;

}
