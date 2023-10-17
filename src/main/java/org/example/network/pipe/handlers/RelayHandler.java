package org.example.network.pipe.handlers;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;
import org.example.network.tcp.TcpClient;
import org.example.network.tcp.TcpClient.Config;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

public class RelayHandler implements PipeHandler {

    private final Pipeline remote;

    public RelayHandler(Pipeline local, String host, int port) throws IOException {
        Config config = new Config();
        config.host = host;
        config.port = port;
        config.handler = new PipeHandler() {

            @Override
            public void init(PipeContext ctx) {
                try {
                    ctx.addFirst(AuthHandlers.client());
                    ctx.addFirst(new SslPipeHandler(SSLContext.getDefault(), true));
                    ctx.addFirst(new LoggingHandler());
                } catch (Exception e) {
                    ctx.fireError(e);
                }
            }

            @Override
            public void onConnected(PipeContext ctx) throws IOException {
                PipeHandler.super.onConnected(ctx);
                local.setAutoRead(true);
                ctx.executor().getSelector().wakeup();
            }

            @Override
            public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
                local.write(buf);
            }
        };
        TcpClient client = TcpClient.open(config);
        remote = client.pipeline();
    }

    @Override
    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        remote.write(buf);
    }
}
