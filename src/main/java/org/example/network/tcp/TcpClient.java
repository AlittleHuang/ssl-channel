package org.example.network.tcp;

import org.example.network.buf.Bytes;
import org.example.network.event.NioEventLoopExecutor;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Objects;


public class TcpClient {

    private final NioEventLoopExecutor executor;
    private final TcpConnection connection;


    public TcpClient(Config config) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(config.handler, "config.handler");
        Objects.requireNonNull(config.host, "config.host");
        executor = config.executor == null
                ? NioEventLoopExecutor.getDefault()
                : config.executor;
        if (executor.getStatus() == NioEventLoopExecutor.STATUS_READY) {
            executor.start();
        }
        SocketChannel channel = SocketChannel.open();
        Pipeline pipeline = new Pipeline(channel);
        pipeline.setAutoRead(config.autoRead);
        pipeline.addFirst(config.handler);
        channel.configureBlocking(false);
        int bufCapacity = config.bufCapacity <= 0 ? Bytes.DEF_CAP : config.bufCapacity;
        connection = new TcpConnection(
                pipeline, channel, bufCapacity
        );
        InetSocketAddress address = new InetSocketAddress(config.host, config.port);
        connection.connect(address);
        executor.register(connection);
    }

    public NioEventLoopExecutor executor() {
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

        public NioEventLoopExecutor executor;

        public String host;

        public int port;

        public PipeHandler handler;

        public int bufCapacity;


        public boolean autoRead = true;
    }

}
