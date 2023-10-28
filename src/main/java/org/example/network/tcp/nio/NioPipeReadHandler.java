package org.example.network.tcp.nio;

import org.example.network.event.NioEventLoopExecutor;
import org.example.network.pipe.Pipeline;
import org.example.network.tcp.PipeReadHandler;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public class NioPipeReadHandler implements PipeReadHandler {
    private static volatile NioPipeReadHandler DEFAULT;

    private final NioEventLoopExecutor executor;

    public NioPipeReadHandler(NioEventLoopExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void handler(SocketChannel channel, Pipeline pipeline, SocketAddress remote) throws IOException {
        channel.configureBlocking(false);
        TcpConnection connection = new TcpConnection(pipeline, channel);
        executor.register(connection);
        if (remote != null) {
            pipeline.connect(remote);
        }
        pipeline.connected();
    }

    public static NioPipeReadHandler getDefault() throws IOException {
        if (DEFAULT == null) {
            synchronized (NioPipeReadHandler.class) {
                if (DEFAULT == null) {
                    DEFAULT = new NioPipeReadHandler(NioEventLoopExecutor.getDefault());
                }
            }
        }
        return DEFAULT;
    }
}
