package org.example.network.channel.pipe.handlers;

import org.example.network.channel.pipe.PipeContext;
import org.example.network.channel.pipe.PipeHandler;

import java.io.IOException;

public class IOExceptionHandler implements PipeHandler {

    @Override
    public void onError(PipeContext ctx, Throwable throwable) {
        if (throwable instanceof IOException) {
            try {
                ctx.fireClose();
            } catch (IOException ignore) {
            }
        } else {
            ctx.fireError(throwable);
        }
    }
}
