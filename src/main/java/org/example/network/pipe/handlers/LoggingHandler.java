package org.example.network.pipe.handlers;

import org.example.log.Logs;
import org.example.network.buf.ByteBufferUtil;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;


public class LoggingHandler implements PipeHandler {

    private static final Logger logger = Logs.getLogger(LoggingHandler.class);

    private Level level = Level.INFO;

    public LoggingHandler() {
    }

    public LoggingHandler(Level level) {
        this.level = level;
    }

    public Logger.Level getLevel() {
        return level;
    }

    public void setLevel(Logger.Level level) {
        this.level = level;
    }

    @Override
    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        logger.log(level, () -> ctx.pipeline().getChannel() + "receive:\n" + new String(ByteBufferUtil.copyAsArray(buf)));
        ctx.fireReceive(buf);
    }

    @Override
    public void onWrite(PipeContext ctx, ByteBuffer buf) throws IOException {
        logger.log(level, () -> ctx.pipeline().getChannel() + "write:\n" + new String(ByteBufferUtil.copyAsArray(buf)));
        ctx.fireWrite(buf);
    }
}
