package org.example.network.pipe.handlers;

import org.example.log.Logs;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;
import org.example.network.tcp.TcpClient;
import org.example.network.tcp.TcpClient.Config;

import java.io.IOException;
import java.lang.System.Logger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;

import static java.lang.System.Logger.Level.*;

public class HttpProxyServerHandler implements PipeHandler {

    private static final Logger logger = Logs.getLogger(HttpProxyServerHandler.class);

    private boolean connectionEstablished;
    private Pipeline local;
    private Pipeline remote;
    private byte[] request = new byte[0];


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
        updateProxyRequest(ctx, buf);
        if (endRequest()) {
            logger.log(TRACE, () -> "request header: " + new String(request));
            ProxyRequest pr = resoleRequestData();
            Config config = new Config();
            config.executor = ctx.executor();
            config.host = pr.host();
            config.port = pr.port();
            logger.log(INFO, "ACCEPT " + pr.method() + " " + config.host + ":" + config.port);
            if ("CONNECT".equals(pr.method)) {
                ctx.pipeline().setAutoRead(false);
                byte[] bytes = "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes();
                ByteBuffer bf = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
                ctx.fireWrite(bf);
            }
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

    private boolean endRequest() {
        byte[] end = "\r\n\r\n".getBytes();
        int rl = request.length;
        int el = end.length;
        if (rl <= el) {
            return false;
        }
        for (int i = el - 1, j = rl - 1; i >= 0; i--, j--) {
            if (end[i] != request[j]) {
                return false;
            }
        }
        return true;
    }

    private ProxyRequest resoleRequestData() {
        byte[] bytes = request;
        String line = getFirstLine(bytes);
        String[] split = line.split(" ");
        if (split.length != 3) {
            throw new IllegalStateException("Request format error");
        }

        String method = split[0].toUpperCase();
        String url = split[1];
        String version = split[2];
        String host;
        int port;
        ByteBuffer forward = null;

        if ("CONNECT".equals(method)) {
            split = url.split(":");
            if (split.length != 2) {
                throw new IllegalStateException("Request format error");
            }
            host = split[0];
            port = Integer.parseInt(split[1]);
        } else if (List.of("GET", "HEAD", "OPTIONS").contains(method)) {
            URI uri = URI.create(url);
            host = uri.getHost();
            port = uri.getPort();
            if (port <= 0) {
                String scheme = uri.getScheme();
                if ("https".equals(scheme)) {
                    port = 443;
                } else if ("http".equals(scheme)) {
                    port = 80;
                } else {
                    throw new IllegalStateException("scheme " + scheme + " error");
                }
            }
            forward = ByteBuffer.wrap(request).asReadOnlyBuffer();
        } else {
            throw new IllegalStateException("method " + method + " not support");
        }
        return new ProxyRequest(version, host, port, method, forward);

    }

    record ProxyRequest(String version, String host, int port, String method, ByteBuffer forward) {


    }

    public static String getFirstLine(byte[] bytes) {
        int firstLineLen = 0;
        for (int i = 1; i < bytes.length; i++) {
            if (bytes[i - 1] == '\r' && bytes[i] == '\n') {
                firstLineLen = i - 1;
                break;
            }
        }
        return new String(bytes, 0, firstLineLen);
    }

    private void updateProxyRequest(PipeContext ctx, ByteBuffer buf) throws IOException {
        int len = request.length + buf.remaining();
        if (len > 8 * 1024) {
            ctx.fireClose();
            throw new IllegalStateException("too lage request");
        }
        byte[] dst = new byte[len];
        System.arraycopy(request, 0, dst, 0, request.length);
        buf.get(dst, request.length, buf.remaining());
        request = dst;
    }


}
