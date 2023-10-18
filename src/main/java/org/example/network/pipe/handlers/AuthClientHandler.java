package org.example.network.pipe.handlers;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AuthClientHandler implements PipeHandler {

    private final byte[] key;

    public AuthClientHandler(byte[] key) {
        this.key = key;
    }

    @Override
    public void onWrite(PipeContext ctx, ByteBuffer buf) throws IOException {
        ctx.fireWrite(ByteBuffer.wrap(key).asReadOnlyBuffer());
        ctx.fireWrite(buf);
        ctx.remove();
    }

}
