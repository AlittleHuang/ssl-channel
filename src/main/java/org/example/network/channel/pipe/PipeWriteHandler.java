package org.example.network.channel.pipe;

import java.io.IOException;
import java.nio.ByteBuffer;

@FunctionalInterface
public interface PipeWriteHandler extends PipeHandler {

    @Override
    void onWrite(PipeContext context, ByteBuffer buf) throws IOException;

}
