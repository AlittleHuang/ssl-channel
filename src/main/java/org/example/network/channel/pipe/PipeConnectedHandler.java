package org.example.network.channel.pipe;

import java.io.IOException;

@FunctionalInterface
public interface PipeConnectedHandler extends PipeHandler {

    @Override
    void onConnected(PipeContext ctx) throws IOException;


}
