package org.example.network.tcp;

import org.example.network.channel.EventLoopExecutor;
import org.example.network.channel.pipe.PipeHandler;
import org.example.network.channel.pipe.Pipeline;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Objects;


public class TcpClient {

    private final EventLoopExecutor executor;
    private final TcpConnection connection;


    public TcpClient(Config config) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(config.handler, "config.handler");
        Objects.requireNonNull(config.host, "config.host");
        executor = config.executor == null
                ? EventLoopExecutor.getDefault()
                : config.executor;
        if (executor.getStatus() == EventLoopExecutor.STATUS_READY) {
            executor.start();
        }
        SocketChannel channel = SocketChannel.open();
        Pipeline pipeline = new Pipeline(channel);
        pipeline.addFirst(config.handler);
        channel.configureBlocking(false);
        int bufCapacity = config.bufCapacity <= 0 ? 1024 * 8 : config.bufCapacity;
        connection = new TcpConnection(
                pipeline, channel, bufCapacity
        );
        executor.register(connection);
        InetSocketAddress address = new InetSocketAddress(config.host, config.port);
        channel.connect(address);
    }

    public EventLoopExecutor executor() {
        return executor;
    }

    public Pipeline pipeline() {
        return connection.pipeline;
    }

    public SocketChannel channel() {
        return connection.channel;
    }

    public int defaultBufCapacity() {
        return connection.bufCapacity;
    }

    public static TcpClient open(Config config) throws IOException {
        return new TcpClient(config);
    }


    public static class Config {

        public EventLoopExecutor executor;

        public String host;

        public int port;

        public PipeHandler handler;

        public int bufCapacity;


    }

}
