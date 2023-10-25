package org.example.network.tcp.io;

import org.example.log.Logs;
import org.example.network.buf.Bytes;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;

import java.io.IOException;
import java.lang.System.Logger;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.Logger.Level.*;

public class TcpServer {
    private static final AtomicInteger threadId = new AtomicInteger();
    private static final AtomicInteger childThreadId = new AtomicInteger();
    public static final Logger logger = Logs.getLogger(TcpServer.class);
    private final Config config;

    public TcpServer(Config config) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(config.handler, "config.handler");
        Objects.requireNonNull(config.host, "config.host");
        this.config = config;
        Thread.ofVirtual()
                .name("tcp-listener-" + config.host + ":" + config.port + threadId.getAndIncrement())
                .start(this::handlerAccept);
    }

    private void handlerAccept() {

        Config config = this.config;
        try (ServerSocketChannel channel = ServerSocketChannel.open()) {
            InetSocketAddress address = new InetSocketAddress(config.host, config.port);
            channel.configureBlocking(true);
            channel.bind(address);
            logger.log(INFO, () -> "tpc server bind " + address + " success");
            while (!Thread.interrupted()) {
                try {
                    SocketChannel child = channel.accept();
                    logger.log(DEBUG, () -> "accept " + child);
                    int bufCapacity = config.bufCapacity <= 0
                            ? Bytes.DEF_CAP : config.bufCapacity;
                    Pipeline pipeline = new Pipeline(child);
                    pipeline.addLast(config.handler);
                    ConnectionTask task = new ConnectionTask(pipeline, bufCapacity);
                    Thread.ofVirtual()
                            .name("tcp-io-thread-" + childThreadId.getAndIncrement())
                            .start(task);
                } catch (Exception e) {
                    logger.log(WARNING, () -> channel + " accept error");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static TcpServer open(Config config) throws IOException {
        return new TcpServer(config);
    }

    public static class Config {

        public String host;

        public int port;

        public PipeHandler handler;

        public int bufCapacity;


        public boolean autoRead = true;
    }


}
