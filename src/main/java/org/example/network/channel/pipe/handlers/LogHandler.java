package org.example.network.channel.pipe.handlers;

import org.example.network.channel.pipe.PipeContext;
import org.example.network.channel.pipe.PipeHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

public class LogHandler implements PipeHandler {

    @Override
    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        System.out.println("receive: " + (buf.limit() - buf.position()) + " bytes");
        PipeHandler.super.onReceive(ctx, buf);
    }

    @Override
    public void onWrite(PipeContext ctx, ByteBuffer buf) throws IOException {
        System.out.println("write: " + (buf.limit() - buf.position()) + " bytes");
        PipeHandler.super.onWrite(ctx, buf);
    }
}
