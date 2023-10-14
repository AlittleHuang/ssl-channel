package org.example.network.channel.pipe;

import org.example.network.buf.ByteBufferAllocator;
import org.example.network.buf.CachedByteBufferAllocator;
import org.example.network.channel.EventLoopExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * <pre>
 * receive                 write
 *   |                       |
 *   v                       v
 * head - 1 - 2 - 3 - ... - tail
 * </pre>
 */
public class Pipeline implements ByteBufferAllocator {

    private final WritableByteChannel channel;

    private final ByteBufferAllocator allocator;

    private final PipeNode head;

    private final PipeNode tail;

    private boolean writable;

    private boolean autoRead = true;

    private boolean requiredRead;

    private EventLoopExecutor executor;


    public Pipeline(WritableByteChannel channel) {
        this.channel = channel;
        this.allocator = CachedByteBufferAllocator.globalHeap();
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

    public boolean isWritable() {
        return writable;
    }

    public void setWritable(boolean writable) {
        this.writable = writable;
    }

    public void connected() throws IOException {
        head.onConnect();
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
        tail.fireClose();
    }

    class HeadHandler implements PipeHandler {
        @Override
        public void onWrite(PipeContext ctx, ByteBuffer buf) throws IOException {
            while (buf.hasRemaining()) {
                channel.write(buf);
            }
            ctx.free(buf);
        }

        @Override
        public void onClose(PipeContext ctx) throws IOException {
            channel.close();
        }

        @Override
        public void onError(PipeContext ctx, Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
    }


    static class TailHandler implements PipeHandler {

        @Override
        public void onReceive(PipeContext ctx, ByteBuffer buf) {
            ctx.free(buf);
        }


    }
}

