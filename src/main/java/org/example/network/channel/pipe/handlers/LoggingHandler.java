package org.example.network.channel.pipe.handlers;

import org.example.network.channel.pipe.PipeContext;
import org.example.network.channel.pipe.PipeHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingHandler implements PipeHandler {

    private static final Logger logger = Logger.getLogger(LoggingHandler.class.getName());

    private Level level = Level.INFO;

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    @Override
    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        logger.log(level, () -> "receive: " + (buf.limit() - buf.position()) + " bytes");
        PipeHandler.super.onReceive(ctx, buf);
    }

    @Override
    public void onWrite(PipeContext ctx, ByteBuffer buf) throws IOException {
        logger.log(level, () -> "write: " + (buf.limit() - buf.position()) + " bytes");
        PipeHandler.super.onWrite(ctx, buf);
    }
}
