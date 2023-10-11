package org.example.network.channel.pipe;

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
public class Pipeline {

    private final PipeNode head;

    private final PipeNode tail;

    private boolean writable;


    public Pipeline(WritableByteChannel channel) {
        head = outbound((context, buf) -> channel.write(buf));
        tail = inbound(new TailHandler());
        PipeNode.link(head, tail);
    }


    public PipeContext addFirst(PipeHandler handler) {
        return head.addAfter(handler);
    }

    public PipeContext addLast(PipeHandler handler) {
        return tail.addBefore(handler);
    }

    public PipeNode outbound(PipeWriteHandler handler) {
        return new PipeNode(this, handler);
    }

    public PipeNode inbound(PipeReadHandler handler) {
        return new PipeNode(this, handler);
    }


    public void onReceive(ByteBuffer buffer) {
        head.onReceive(buffer);
    }

    public void onWrite(ByteBuffer buffer) throws IOException {
        tail.onWrite(buffer);
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

    static class TailHandler implements PipeReadHandler {

        @Override
        public void onReceive(PipeContext context, ByteBuffer buf) {
            context.free(buf);
        }
    }
}

