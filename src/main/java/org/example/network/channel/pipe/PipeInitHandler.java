package org.example.network.channel.pipe;

@FunctionalInterface
public interface PipeInitHandler extends PipeHandler {

    @Override
    void init(PipeContext ctx);

}
