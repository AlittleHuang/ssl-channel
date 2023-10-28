package org.example.network.pipe.handlers;

import org.example.log.Logs;
import org.example.network.pipe.HttpProxyHeaderReader;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;
import org.example.network.tcp.PipeReadHandler;
import org.example.network.tcp.TcpClient;
import org.example.network.tcp.TcpClient.Config;
import org.example.network.tcp.bio.BlockingPipeReadHandler;

import java.io.IOException;
import java.lang.System.Logger;
import java.nio.ByteBuffer;

import static java.lang.System.Logger.Level.INFO;

public class HttpProxyServerHandler implements PipeHandler {

    private static final Logger logger = Logs.getLogger(HttpProxyServerHandler.class);

    private final PipeReadHandler readHandler;
    private boolean connectionEstablished;
    private Pipeline local;
    private Pipeline remote;
    private final HttpProxyHeaderReader reader;

    public HttpProxyServerHandler() {
        this(BlockingPipeReadHandler.DEFAULT, new HttpProxyHeaderReader());
    }

    public HttpProxyServerHandler(PipeReadHandler readHandler, HttpProxyHeaderReader reader) {
        this.readHandler = readHandler;
        this.reader = reader;
    }


    @Override
    public void init(PipeContext ctx) {
        ctx.addBefore(new IOExceptionHandler());
        local = ctx.pipeline();
    }

    @Override
    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        if (connectionEstablished) {
            remote.write(buf);
        } else {
            establishConnection(ctx, buf);
            ctx.free(buf);
        }
    }

    private void establishConnection(PipeContext ctx, ByteBuffer buf) throws IOException {
        if (reader.update(buf)) {
            ProxyRequest pr = reader.getRequest();
            logger.log(INFO, "ACCEPT " + pr.method() + " " + pr.host + ":" + pr.port);
            if ("CONNECT".equals(pr.method)) {
                ctx.pipeline().setAutoRead(false);
                byte[] bytes = "HTTP/1.1 200 Connection established\r\n\r\n".getBytes();
                ByteBuffer bf = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
                ctx.fireWrite(bf);
            }
            Config config = new Config();
            config.reader = readHandler;
            config.host = pr.host();
            config.port = pr.port();
            config.handler = new PipeHandler() {

                @Override
                public void init(PipeContext ctx) {
                    ctx.addBefore(new IOExceptionHandler());
                }

                @Override
                public void onConnected(PipeContext ctx) throws IOException {
                    if (!"CONNECT".equals(pr.method)) {
                        ctx.fireWrite(pr.forward());
                    } else {
                        local.setAutoRead(true);
                    }
                    ctx.fireConnected();
                }

                @Override
                public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
                    local.write(buf);
                }

                @Override
                public void onError(PipeContext ctx, Throwable throwable) {
                    try {
                        local.close();
                        remote.close();
                    } catch (Exception e) {
                        // ctx.fireError(e);
                    }
                    ctx.fireError(throwable);
                }
            };
            TcpClient client = TcpClient.open(config);
            remote = client.pipeline();
            connectionEstablished = true;
        }
    }

    @Override
    public void onError(PipeContext ctx, Throwable throwable) {
        try {
            local.close();
            remote.close();
        } catch (Exception e) {
            // ctx.fireError(e);
        }
        ctx.fireError(throwable);
    }


    public record ProxyRequest(String version, String host, int port, String method, ByteBuffer forward) {
    }


}
