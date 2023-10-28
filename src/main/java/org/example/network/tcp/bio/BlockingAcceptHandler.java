package org.example.network.tcp.bio;

import org.example.log.Logs;
import org.example.network.pipe.Pipeline;
import org.example.network.tcp.ChannelAcceptHandler;
import org.example.network.tcp.PipeConfig;
import org.example.network.tcp.PipeReadHandler;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

public class BlockingAcceptHandler implements ChannelAcceptHandler {

    private static final Logger logger = Logs.getLogger(BlockingAcceptHandler.class);
    public static final BlockingAcceptHandler DEFAULT = new BlockingAcceptHandler();

    private BlockingAcceptHandler() {
    }

    @Override
    public CompletableFuture<Void> handler(ServerSocketChannel channel,
                                           PipeReadHandler reader,
                                           PipeConfig config,
                                           InetSocketAddress local) {
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        Thread.ofVirtual()
                .name(String.valueOf(local))
                .start(() -> {
                    try {
                        handlerAccept(channel, reader, config, local);
                    } finally {
                        closeFuture.complete(null);
                    }
                });
        return closeFuture;
    }

    private void handlerAccept(ServerSocketChannel channel,
                               PipeReadHandler reader,
                               PipeConfig config,
                               InetSocketAddress local) {
        try {
            channel.configureBlocking(true);
            channel.bind(local);
            logger.log(Level.INFO, local + " bind success");
        } catch (IOException e) {
            logger.log(Level.ERROR, "", e);
            return;
        }
        Thread thread = Thread.currentThread();
        while (!thread.isInterrupted()) {
            try {
                SocketChannel ch = channel.accept();
                logger.log(Level.DEBUG, () -> ch + " accept");
                Pipeline pipeline = new Pipeline(ch, config);
                reader.handler(ch, pipeline, null);
            } catch (Exception e) {
                logger.log(Level.ERROR, "", e);
            }
        }
    }
}
