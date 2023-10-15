package org.example.network.pipe.handlers;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;

public class HttpProxyServerInitializer implements PipeHandler {


    @Override
    public void init(PipeContext ctx) {
        try {
            initHandler(ctx);
        } finally {
            ctx.remove();
        }
    }

    private void initHandler(PipeContext ctx) {
        ctx.replace(new HttpProxyServerHandler());
    }


}

