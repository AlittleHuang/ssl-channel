package org.example.network.tcp;

import org.example.log.Logs;
import org.example.network.buf.Bytes;
import org.example.network.event.EventLoopExecutor;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;

import java.io.IOException;
import java.lang.System.Logger;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Objects;

import static java.lang.System.Logger.Level.DEBUG;


public class TcpServer {

    private static Logger logger = Logs.getLogger(TcpServer.class);

    private final EventLoopExecutor executor;
    private final ServerSocketChannel channel;
    private final int bufCapacity;


    public TcpServer(Config config) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(config.handler, "config.handler");
        Objects.requireNonNull(config.host, "config.host");
        executor = config.executor == null
                ? EventLoopExecutor.getDefault()
                : config.executor;
        if (executor.getStatus() == EventLoopExecutor.STATUS_READY) {
            executor.start();
        }

        this.bufCapacity = config.bufCapacity <= 0
                ? Bytes.DEF_CAP : config.bufCapacity;
        this.channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        executor.register(channel, SelectionKey.OP_ACCEPT, key -> {
            if (key.isAcceptable()) {
                SocketChannel child = channel.accept();
                if (child == null) {
                    return;
                }
                logger.log(DEBUG, () -> "accept " + child);
                child.configureBlocking(false);
                Pipeline pipeline = new Pipeline(child);
                pipeline.setAutoRead(config.autoRead);
                pipeline.addFirst(config.handler);

                TcpConnection connection = new TcpConnection(
                        pipeline, child, bufCapacity
                );
                executor.register(connection);
            }
        });
        InetSocketAddress address = new InetSocketAddress(config.host, config.port);
        channel.bind(address);
        logger.log(DEBUG, () -> "tpc server bind " + address + " success");
    }

    public EventLoopExecutor executor() {
        return executor;
    }

    public ServerSocketChannel channel() {
        return channel;
    }

    public int defaultBufCapacity() {
        return bufCapacity;
    }

    public static TcpServer open(Config config) throws IOException {
        return new TcpServer(config);
    }


    public static class Config {

        public EventLoopExecutor executor;

        public String host = "127.0.0.1";

        public int port;

        public PipeHandler handler;

        public int bufCapacity;

        public boolean autoRead = true;


    }

}
