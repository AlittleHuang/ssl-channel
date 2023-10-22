package org.example.network.jray;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.handlers.AuthHandlers;

import java.io.IOException;
import java.nio.ByteBuffer;

public class KeeperServerHandler extends ConnectionKeeper {

    @Override
    public void init(PipeContext ctx) {
        ctx.addBefore(AuthHandlers.server());
    }

    @Override
    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        try {
            doOnReceive(ctx, buf);
        } finally {
            ctx.free(buf);
        }
    }

    private static void doOnReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        if (buf.remaining() != 1) {
            ctx.fireClose();
        } else {
            byte b = buf.get();
            if (b == KEEP) {
                ctx.fireWrite(ByteBuffer.wrap(new byte[]{KEEP}));
            } else if (b == REMOVE) {
                ctx.fireWrite(ByteBuffer.wrap(new byte[]{REMOVE}));
                ctx.remove();
            } else {
                ctx.fireClose();
            }
        }
    }

}
