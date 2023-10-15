package org.example.network.pipe.handlers;

import org.example.log.Logs;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

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
                    logger.warning("auth field");
                    ctx.fireClose();
                    return;
                }
            }
            if (checkedLen == key.length) {
                ctx.fireReceive(buf);
                ctx.remove();
            }
        }
    }

}
