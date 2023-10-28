package org.example.network.tcp.nio;

import org.example.log.Logs;
import org.example.network.event.NioEventLoopExecutor;
import org.example.network.pipe.Pipeline;
import org.example.network.tcp.ChannelAcceptHandler;
import org.example.network.tcp.PipeConfig;
import org.example.network.tcp.PipeReadHandler;

import java.io.IOException;
import java.lang.System.Logger;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CompletableFuture;

import static java.lang.System.Logger.Level.DEBUG;

public class NioChannelAcceptHandler implements ChannelAcceptHandler {
    private static final Logger logger = Logs.getLogger(NioChannelAcceptHandler.class);

    private static volatile NioChannelAcceptHandler DEFAULT;

    private final NioEventLoopExecutor executor;

    public NioChannelAcceptHandler(NioEventLoopExecutor executor) {
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> handler(ServerSocketChannel channel,
                                           PipeReadHandler reader,
                                           PipeConfig config,
                                           InetSocketAddress bind) throws IOException {
        CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        executor.register(channel, SelectionKey.OP_ACCEPT, key -> {
            if (key.isAcceptable()) {
                SocketChannel child;
                try {
                    child = channel.accept();
                } catch (IOException e) {
                    closeFuture.complete(null);
                    throw e;
                }
                if (child == null) {
                    return;
                }
                logger.log(DEBUG, () -> "accept " + child);
                child.configureBlocking(false);
                Pipeline pipeline = new Pipeline(child, config);
                reader.handler(child, pipeline, null);
            }
        });
        return closeFuture;
    }

    public static NioChannelAcceptHandler getDefault() throws IOException {
        if (DEFAULT == null) {
            synchronized (NioChannelAcceptHandler.class) {
                if (DEFAULT == null) {
                    DEFAULT = new NioChannelAcceptHandler(NioEventLoopExecutor.getDefault());
                }
            }
        }
        return DEFAULT;
    }
}
