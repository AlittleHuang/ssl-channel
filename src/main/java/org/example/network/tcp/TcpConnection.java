package org.example.network.tcp;

import org.example.network.event.EventLoopExecutor;
import org.example.network.event.SelectionKeyHandler;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

class TcpConnection implements SelectionKeyHandler {

    final Pipeline pipeline;
    final SocketChannel channel;
    final int bufCapacity;

    TcpConnection(Pipeline pipeline, SocketChannel channel, int bufCapacity) {
        this.pipeline = pipeline;
        this.channel = channel;
        this.bufCapacity = bufCapacity;
    }

    @Override
    public void init(EventLoopExecutor executor) {
        pipeline.executor(executor);
    }

    @Override
    public SelectableChannel channel() {
        return channel;
    }

    @Override
    public int registerOps() {
        return channel.validOps();
    }

    @Override
    public void handler(SelectionKey key) throws IOException {
        try {
            doHandler(key);
        } catch (Throwable e) {
            pipeline.onError(e);
        }
    }

    private void doHandler(SelectionKey key) throws IOException {
        if (!key.isValid()) {
            return;
        }
        if (key.isConnectable()) {
            if (channel.finishConnect()) {
                pipeline.connected();
            }
        }
        if (key.isWritable()) {
            key.interestOps(SelectionKey.OP_READ);
        }
        if (key.isReadable() && (pipeline.isAutoRead() || pipeline.isRequiredRead())) {
            pipeline.setRequiredRead(false);
            ByteBuffer buf = pipeline.allocate(bufCapacity);
            int read;
            while ((read = channel.read(buf)) > 0) {
                if (buf.position() == buf.limit()) {
                    pipeline.onReceive(buf.flip());
                    buf = pipeline.allocate(bufCapacity);
                }
            }
            if (buf.flip().hasRemaining()) {
                pipeline.onReceive(buf);
            }
            if (read == -1) {
                pipeline.onReceive(PipeHandler.END_OF_STREAM);
                if (key.isValid()) {
                    key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
                }
            }
        }
    }
}
