package org.example.network.tcp;

import org.example.network.tcp.bio.BlockingAcceptHandler;
import org.example.network.tcp.bio.BlockingPipeReadHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CompletableFuture;

public class TcpServer {


    private final ServerSocketChannel channel;

    private final CompletableFuture<Void> closeFuture;

    public TcpServer(Config config) throws IOException {
        InetSocketAddress address = new InetSocketAddress(config.bindHost, config.bindPort);
        ServerSocketChannel channel = ServerSocketChannel.open();
        CompletableFuture<Void> closeFuture =
                config.accepter.handler(channel, config.reader, config, address);
        this.channel = channel;
        this.closeFuture = closeFuture;
    }

    public void close() throws IOException {
        channel.close();
    }

    public CompletableFuture<Void> closeFuture() {
        return closeFuture;
    }

    public static TcpServer open(Config config) throws IOException {
        return new TcpServer(config);
    }


    public static class Config extends PipeConfig {

        public String bindHost = "0.0.0.0";

        public int bindPort;

        public PipeReadHandler reader = BlockingPipeReadHandler.DEFAULT;

        public ChannelAcceptHandler accepter = BlockingAcceptHandler.DEFAULT;

    }
}

