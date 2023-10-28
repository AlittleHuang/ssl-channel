package org.example.network.jray;

import org.example.log.Logs;
import org.example.network.buf.ByteBufferUtil;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;
import org.example.network.pipe.handlers.IOExceptionHandler;
import org.example.network.tcp.PipeReadHandler;
import org.example.network.tcp.TcpClient;
import org.example.network.tcp.TcpClient.Config;

import java.io.IOException;
import java.lang.System.Logger;
import java.nio.ByteBuffer;

import static java.lang.System.Logger.Level.INFO;

public class HttpProxyRemoteHandler implements PipeHandler {

    private static final Logger logger = Logs.getLogger(HttpProxyRemoteHandler.class);
    private final PipeReadHandler readHandler;

    public HttpProxyRemoteHandler(PipeReadHandler readHandler) {
        this.readHandler = readHandler;
    }

    @Override
    public void init(PipeContext ctx) {
        ctx.replace(new Handler());
    }

    private class Handler implements PipeHandler {

        private Pipeline local;
        private Pipeline remote;

        private ByteBuffer targetAddress;

        @Override
        public void init(PipeContext ctx) {
            local = ctx.pipeline();
        }

        @Override
        public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
            if (remote != null) {
                remote.write(buf);
            } else {
                establishConnection(ctx, buf);
                ctx.free(buf);
            }
        }

        private void establishConnection(PipeContext ctx, ByteBuffer buf) throws IOException {
            boolean read = false;
            if (targetAddress == null) {
                targetAddress = ctx.pipeline().allocate();
            }
            while (buf.hasRemaining()) {
                byte b = buf.get();
                if (b == '\n') {
                    read = true;
                    break;
                }
                targetAddress.put(b);
            }
            if (!buf.hasRemaining()) {
                ctx.free(buf);
            }
            if (read) {
                local.setAutoRead(false);
                String target = ByteBufferUtil.readToString(targetAddress.flip());
                ctx.free(targetAddress);
                String[] split = target.split(":");
                String host = split[0];
                int port = Integer.parseInt(split[1]);
                logger.log(INFO, "ACCEPT " + target);

                Config config = new Config();
                config.reader = readHandler;
                config.host = host;
                config.port = port;
                config.handler = new PipeHandler() {

                    @Override
                    public void init(PipeContext ctx) {
                        ctx.addBefore(new IOExceptionHandler());
                    }

                    @Override
                    public void onConnected(PipeContext ctx) throws IOException {
                        ctx.fireConnected();
                        local.setAutoRead(true);
                        if (buf.hasRemaining()) {
                            remote.write(buf);
                        }
                    }

                    @Override
                    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
                        local.write(buf);
                    }

                    @Override
                    public void onClose(PipeContext ctx) throws IOException {
                        PipeHandler.super.onClose(ctx);
                        local.close();
                    }
                };
                TcpClient client = TcpClient.open(config);
                remote = client.pipeline();

            }
        }

        @Override
        public void onClose(PipeContext ctx) throws IOException {
            PipeHandler.super.onClose(ctx);
            remote.close();
        }
    }
}
