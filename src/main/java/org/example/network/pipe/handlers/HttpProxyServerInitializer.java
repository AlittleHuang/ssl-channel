package org.example.network.pipe.handlers;

import org.example.network.pipe.HttpProxyHeaderReader;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.tcp.PipeReadHandler;
import org.example.network.tcp.bio.BlockingPipeReadHandler;

public class HttpProxyServerInitializer implements PipeHandler {

    private final PipeReadHandler readHandler;

    public HttpProxyServerInitializer() {
        this(BlockingPipeReadHandler.DEFAULT);
    }

    public HttpProxyServerInitializer(PipeReadHandler readHandler) {
        this.readHandler = readHandler;
    }

    @Override
    public void init(PipeContext ctx) {
        try {
            initHandler(ctx);
        } finally {
            ctx.remove();
        }
    }

    private void initHandler(PipeContext ctx) {
        var reader = new HttpProxyHeaderReader(ctx.pipeline());
        ctx.replace(new HttpProxyServerHandler(readHandler, reader));
    }


}

