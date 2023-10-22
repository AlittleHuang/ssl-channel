package org.example.network.pipe.handlers;

import org.example.log.Logs;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

public class IOExceptionHandler implements PipeHandler {

    Logger logger = Logs.getLogger(IOExceptionHandler.class);


    @Override
    public void onError(PipeContext ctx, Throwable throwable) {
        if (throwable instanceof IOException) {
            try {
                ctx.fireClose();
            } catch (IOException e) {
                logger.log(Level.DEBUG, () -> "error: " + throwable.getClass().getName() + " : " + e.getLocalizedMessage());
            }
        } else {
            ctx.fireError(throwable);
        }
    }
}
