package org.example.network.tcp;

import org.example.network.pipe.Pipeline;
import org.example.network.tcp.bio.BlockingPipeReadHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class TcpClient {

    private final Pipeline pipeline;

    public TcpClient(Config config) throws IOException {
        InetSocketAddress remote = config.host != null
                ? new InetSocketAddress(config.host, config.port)
                : new InetSocketAddress(config.port);
        SocketChannel channel = SocketChannel.open();
        pipeline = new Pipeline(channel, config);
        config.reader.handler(channel, pipeline, remote);
    }

    public Pipeline pipeline() {
        return pipeline;
    }

    public static TcpClient open(Config config) throws IOException {
        return new TcpClient(config);
    }


    public static class Config extends PipeConfig {

        public String host;

        public int port;

        public PipeReadHandler reader = BlockingPipeReadHandler.DEFAULT;

    }
}

