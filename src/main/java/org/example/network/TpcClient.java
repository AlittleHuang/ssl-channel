package org.example.network;

import lombok.Builder;
import org.example.network.channel.SelectorService;
import org.example.network.channel.handler.ChannelHandler;
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

    private final SelectorService selectorService;
    private final Pipeline pipeline;
    private final SocketChannel channel;
    private final int bufCapacity;


    public TpcClient(Config config) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(config.handler, "config.handler");
        Objects.requireNonNull(config.host, "config.host");
        selectorService = config.service == null
                ? new SelectorService(Selector.open())
                : config.service;
        if (selectorService.getStatus() == SelectorService.STATUS_READY) {
            selectorService.start();
        }
        channel = SocketChannel.open();
        pipeline = new Pipeline(channel);
        pipeline.addFirst(config.handler);
        channel.configureBlocking(false);
        selectorService.register(new PipelineChannelHandler());
        bufCapacity = config.bufCapacity <= 0 ? 1024 * 8 : config.bufCapacity;
        InetSocketAddress address = new InetSocketAddress(config.host, config.port);
        channel.connect(address);
    }

    public static TpcClient open(Config config) throws IOException {
        return new TpcClient(config);
    }

    class PipelineChannelHandler implements ChannelHandler {
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
            if (key.isReadable()) {
                ByteBuffer buf = selectorService.getAllocator().allocate(bufCapacity);
                int read;
                while ((read = channel.read(buf)) > 0) {
                    if (buf.position() == buf.limit()) {
                        buf.flip();
                        pipeline.onReceive(buf.flip());
                        buf = selectorService.getAllocator().allocate(bufCapacity);
                    }
                }
                if (buf.flip().hasRemaining()) {
                    pipeline.onReceive(buf);
                }
                if (read == -1) {

                }
            }
        }
    }


    @Builder
    public static class Config {

        private SelectorService service;

        private String host;

        private int port;

        private PipeHandler handler;

        private int bufCapacity;


    }

}
