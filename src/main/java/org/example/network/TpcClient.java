package org.example.network;

import org.example.network.channel.EventLoopExecutor;
import org.example.network.channel.handler.SelectionKeyHandler;
import org.example.network.channel.pipe.PipeHandler;
import org.example.network.channel.pipe.Pipeline;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Objects;


public class TpcClient {

    private final EventLoopExecutor executor;
    private final Pipeline pipeline;
    private final SocketChannel channel;
    private final int bufCapacity;


    public TpcClient(Config config) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(config.handler, "config.handler");
        Objects.requireNonNull(config.host, "config.host");
        executor = config.executor == null
                ? new EventLoopExecutor(Selector.open())
                : config.executor;
        if (executor.getStatus() == EventLoopExecutor.STATUS_READY) {
            executor.start();
        }
        channel = SocketChannel.open();
        pipeline = new Pipeline(channel);
        pipeline.addFirst(config.handler);
        channel.configureBlocking(false);
        executor.register(new PipelineChannelHandler());
        bufCapacity = config.bufCapacity <= 0 ? 1024 * 8 : config.bufCapacity;
        InetSocketAddress address = new InetSocketAddress(config.host, config.port);
        channel.connect(address);
    }

    public EventLoopExecutor executor() {
        return executor;
    }

    public Pipeline pipeline() {
        return pipeline;
    }

    public SocketChannel channel() {
        return channel;
    }

    public int defaultBufCapacity() {
        return bufCapacity;
    }

    public static TpcClient open(Config config) throws IOException {
        return new TpcClient(config);
    }

    class PipelineChannelHandler implements SelectionKeyHandler {

        @Override
        public void init(EventLoopExecutor executor) {
            pipeline.executor(executor);
        }

        @Override
        public SelectableChannel channel() {
            return channel;
        }

        @Override
        public int registerOps() {
            return channel.validOps();
        }

        @Override
        public void handler(SelectionKey key) throws IOException {
            if (key.isConnectable()) {
                if (channel.finishConnect()) {
                    pipeline.connected();
                }
            }
            if (key.isWritable()) {
                pipeline.setWritable(true);
                key.interestOps(SelectionKey.OP_READ);
            }
            if (key.isReadable() && (pipeline.isAutoRead() || pipeline.isRequiredRead())) {
                ByteBuffer buf = pipeline().allocate(bufCapacity);
                int read;
                while ((read = channel.read(buf)) > 0) {
                    if (buf.position() == buf.limit()) {
                        pipeline.onReceive(buf.flip());
                        buf = pipeline().allocate(bufCapacity);
                    }
                }
                if (buf.flip().hasRemaining()) {
                    pipeline.onReceive(buf);
                }
                if (read == -1) {
                    pipeline.onReceive(PipeHandler.END_OF_STREAM);
                    if (key.isValid()) {
                        key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
                    }
                }
            }
        }
    }


    public static class Config {

        public EventLoopExecutor executor;

        public String host;

        public int port;

        public PipeHandler handler;

        public int bufCapacity;


    }

}
