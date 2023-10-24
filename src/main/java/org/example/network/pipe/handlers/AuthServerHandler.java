package org.example.network.pipe.handlers;

import org.example.log.Logs;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class AuthServerHandler implements PipeHandler {

    private static final Logger logger = Logs.getLogger(AuthServerHandler.class);

    private final byte[] key;

    public AuthServerHandler(byte[] key) {
        this.key = key;
    }

    @Override
    public void init(PipeContext ctx) {
        ctx.replace(new Checker());
    }

    class Checker implements PipeHandler {
        private int checkedLen;

        @Override
        public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
            while (checkedLen < key.length && buf.hasRemaining()) {
                if (key[checkedLen++] != buf.get()) {
                    logger.log(Level.WARNING, "auth field");
                    ctx.fireWrite(ByteBuffer.wrap("HTTP/1.1 403 FORBIDDEN\r\n\r\n".getBytes()));
                    ctx.fireClose();
                    ctx.free(buf);
                    return;
                }
            }
            if (checkedLen == key.length) {
                ctx.fireConnected();
                if (buf.hasRemaining()) {
                    ctx.fireReceive(buf);
                } else {
                    ctx.free(buf);
                }
                ctx.remove();
            }
        }

        @Override
        public void onConnect(PipeContext ctx, InetSocketAddress address) {

        }
    }

}
