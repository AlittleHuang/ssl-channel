package org.example.network.channel.pipe;

import java.nio.ByteBuffer;

@FunctionalInterface
public interface PipeReadHandler extends PipeHandler {


    @Override
    void onReceive(PipeContext context, ByteBuffer buf);


}
