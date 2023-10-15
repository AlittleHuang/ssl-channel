package org.example.network.pipe.handlers;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;

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
