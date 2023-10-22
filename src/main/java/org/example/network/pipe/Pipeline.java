package org.example.network.pipe;

import org.example.log.Logs;
import org.example.network.buf.ByteBufferAllocator;
import org.example.network.buf.ByteBufferUtil;
import org.example.network.buf.PooledAllocator;
import org.example.network.event.EventLoopExecutor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

import static java.lang.System.Logger.Level.*;

/**
 * <pre>
 * inbound                outbound
 *   |                       |
 *   v                       v
 * head - 1 - 2 - 3 - ... - tail
 * </pre>
 */
public class Pipeline implements ByteBufferAllocator {

    private static final Logger logger = Logs.getLogger(Pipeline.class);


    private final WritableByteChannel channel;

    private final ByteBufferAllocator allocator;

    private final PipeNode head;

    private final PipeNode tail;

    private boolean autoRead = true;

    private boolean requiredRead;

    private EventLoopExecutor executor;
    private boolean closed;


    public Pipeline(WritableByteChannel channel) {
        this.channel = channel;
        this.allocator = PooledAllocator.GLOBAL;
        this.head = node(new HeadHandler());
        this.tail = node(new TailHandler());
        PipeNode.link(head, tail);
    }


    public PipeContext addFirst(PipeHandler handler) {
        return head.addAfter(handler);
    }

    public PipeContext addLast(PipeHandler handler) {
        return tail.addBefore(handler);
    }

    public PipeNode node(PipeHandler handler) {
        return new PipeNode(this, handler);
    }


    public void onReceive(ByteBuffer buffer) throws IOException {
        head.onReceive(buffer);
    }

    public void write(ByteBuffer buffer) throws IOException {
        tail.onWrite(buffer);
    }

    public void onError(Throwable throwable) {
        tail.onError(throwable);
    }

    public void connected() throws IOException {
        head.onConnected();
    }

    public WritableByteChannel getChannel() {
        return channel;
    }

    public void read() {
        if (!autoRead) {
            requiredRead = true;
        }
    }

    public boolean isRequiredRead() {
        return requiredRead;
    }

    public void setRequiredRead(boolean requiredRead) {
        this.requiredRead = requiredRead;
    }

    public void setAutoRead(boolean autoRead) {
        this.autoRead = autoRead;
    }

    public boolean isAutoRead() {
        return autoRead;
    }

    public void executor(EventLoopExecutor executor) {
        this.executor = executor;
    }

    public EventLoopExecutor executor() {
        return executor;
    }

    @Override
    public ByteBuffer allocate(int capacity) {
        return allocator.allocate(capacity);
    }

    @Override
    public void free(ByteBuffer buffer) {
        allocator.free(buffer);
    }

    public void close() throws IOException {
        this.closed = true;
        tail.fireClose();
    }

    public void connect(InetSocketAddress address) throws IOException {
        tail.fireConnect(address);
    }

    public boolean isClosed() {
        return closed || !channel.isOpen();
    }

    static class HeadHandler implements PipeHandler {
        @Override
        public void onWrite(PipeContext ctx, ByteBuffer buf) throws IOException {
            try {
                while (buf.hasRemaining()) {
                    ctx.pipeline().channel.write(buf);
                }
            } catch (Exception e) {
                ctx.pipeline().onError(e);
            } finally {
                ctx.free(buf);
            }
        }

        @Override
        public void onClose(PipeContext ctx) throws IOException {
            try {
                ctx.pipeline().channel.close();
            } catch (IOException e) {
                logger.log(WARNING, () -> ctx.pipeline().getChannel() + " close error: " + e.getClass().getName() + " : " + e.getLocalizedMessage());
            }
        }

        @Override
        public void onError(PipeContext ctx, Throwable throwable) {
            if (throwable instanceof IOException) {
                logger.log(INFO, () -> ctx.pipeline().getChannel() + " - Error reached end of pipeline, close channel: "
                                       + throwable.getClass().getName() + " : " + throwable.getLocalizedMessage());
            } else {
                logger.log(WARNING, () -> ctx.pipeline().getChannel() + " - Error reached end of pipeline, close channel", throwable);
            }
            try {
                ctx.pipeline().channel.close();
            } catch (IOException e) {
                logger.log(WARNING, "close channel error", e);
            }
        }

        @Override
        public void onConnect(PipeContext context, InetSocketAddress address) throws IOException {
            if (context.pipeline().getChannel() instanceof SocketChannel channel) {
                channel.connect(address);
            } else {
                throw new UnsupportedEncodingException();
            }
        }
    }


    static class TailHandler implements PipeHandler {

        @Override
        public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
            if (buf.hasRemaining()) {
                logger.log(WARNING, () -> "Buffer has " + buf.remaining() + "remaining reached end of pipeline");
            }
            if (buf == END_OF_STREAM) {
                ctx.pipeline().close();
            }
            ctx.free(buf);
        }

        @Override
        public void onConnected(PipeContext ctx) {
            logger.log(DEBUG, () -> ctx.pipeline().channel + " connected");
        }
    }
}

