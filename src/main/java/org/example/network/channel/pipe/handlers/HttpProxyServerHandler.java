package org.example.network.channel.pipe.handlers;

import org.example.network.channel.pipe.PipeContext;
import org.example.network.channel.pipe.PipeHandler;
import org.example.network.channel.pipe.Pipeline;
import org.example.network.channel.pipe.handlers.HttpProxyServerHandler.HandlerImpl.ProxyRequest.Connect;
import org.example.network.channel.pipe.handlers.HttpProxyServerHandler.HandlerImpl.ProxyRequest.Get;
import org.example.network.tcp.TcpClient;
import org.example.network.tcp.TcpClient.Config;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

public class HttpProxyServerHandler implements PipeHandler {
    private static final Logger logger = Logger
            .getLogger(HttpProxyServerHandler.class.getName());

    @Override
    public void init(PipeContext ctx) {
        try {
            initHandler(ctx);
        } finally {
            ctx.remove();
        }
    }

    private void initHandler(PipeContext ctx) {
        ctx.replace(new HandlerImpl());
    }

    static class HandlerImpl implements PipeHandler {

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
            }
        }

        private void establishConnection(PipeContext ctx, ByteBuffer buf) throws IOException {
            updateReq(ctx, buf);
            if (endRequest()) {
                logger.fine(() -> new String(request));
                ProxyRequest pr = resoleRequestData();
                Config config = new Config();
                config.executor = ctx.executor();
                config.host = pr.targetHost();
                config.port = pr.targetPort();

                if (pr instanceof Connect) {
                    ctx.pipeline().setAutoRead(false);
                    byte[] bytes = "HTTP/1.1 OK Connection Established\r\n\r\n".getBytes();
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
                        if (pr instanceof Get get) {
                            ctx.fireWrite(get.forward());
                        } else {
                            local.setAutoRead(true);
                        }
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
            String method = split[0];
            String url = split[1];
            String version = split[2];

            String host;
            int port;

            if ("CONNECT".equals(method)) {
                // if (!"HTTP/1.1".equals(version)) {
                //     throw new IllegalStateException(
                //             "version " + version + " not support");
                // }
                split = url.split(":");
                if (split.length != 2) {
                    throw new IllegalStateException("Request format error");
                }
                host = split[0];
                port = Integer.parseInt(split[1]);
                return new Connect(host, port);
            } else if ("GET".equals(method)) {
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
                return new Get(host, port, ByteBuffer.wrap(request).asReadOnlyBuffer());
            } else {
                throw new IllegalStateException("method " + method + " not support");
            }

        }

        interface ProxyRequest {

            String targetHost();

            int targetPort();

            record Get(
                    String targetHost,
                    int targetPort,
                    ByteBuffer forward
            ) implements ProxyRequest {

            }

            record Connect(
                    String targetHost,
                    int targetPort
            ) implements ProxyRequest {
            }

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

        private void updateReq(PipeContext ctx, ByteBuffer buf) throws IOException {
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


    public static void main(String[] args) {
        String test = """
                GET https://www.baidu.com HTTP/1.1\r
                Host: www.baidu.com\r
                \r
                """;

        String firstLine = HandlerImpl.getFirstLine(test.getBytes());
        System.out.println(firstLine);
    }

}

