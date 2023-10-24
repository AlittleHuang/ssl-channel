package org.example.network.tcp;

import org.example.log.Logs;
import org.example.network.event.EventLoopExecutor;
import org.example.network.event.SelectionKeyHandler;
import org.example.network.pipe.Pipeline;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;

class TcpConnection implements SelectionKeyHandler {
    private static final Logger logger = Logs.getLogger(TcpConnection.class);

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
        return isReadable() ? OP_CONNECT | OP_READ : OP_CONNECT;
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
        if (key.isConnectable() && channel.finishConnect()) {
            pipeline.connected();
            if (!key.isValid()) {
                return;
            }
            key.interestOps(key.interestOps() & (~OP_CONNECT));
        }
        if (key.isReadable() && isReadable()) {
            pipeline.setRequiredRead(false);
            ByteBuffer buf = pipeline.allocate(bufCapacity);
            int read;
            while ((read = read(buf)) > 0) {
                if (buf.position() == buf.limit()) {
                    buf.flip();
                    receive(buf);
                    buf = pipeline.allocate(bufCapacity);
                }
            }
            if (buf.flip().hasRemaining()) {
                receive(buf);
            } else {
                pipeline.free(buf);
            }
            if (read == -1) {
                pipeline.onReadeTheEnd();
                if (key.isValid()) {
                    key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
                }
            }
        }
        if (!isReadable()) {
            key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
        }
    }

    private boolean isReadable() {
        return pipeline.isAutoRead() || pipeline.isRequiredRead();
    }

    private void receive(ByteBuffer buf) throws IOException {
        pipeline.onReceive(buf);
    }

    private int read(ByteBuffer buf) {
        try {
            return channel.read(buf);
        } catch (IOException e) {
            logger.log(Level.DEBUG, () -> "read filed: " + e.getClass().getName() + " : " + e.getLocalizedMessage());
            return -1;
        } catch (Exception e) {
            logger.log(Level.DEBUG, () -> "read filed: " + e.getClass().getName() + " : " + e.getLocalizedMessage());
            if (!buf.hasRemaining()) {
                pipeline.free(buf);
                throw e;
            } else {
                return 0;
            }
        }
    }

    public void connect(InetSocketAddress address) throws IOException {
        pipeline.connect(address);
    }
}
