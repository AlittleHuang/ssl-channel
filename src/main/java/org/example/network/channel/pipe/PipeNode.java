package org.example.network.channel.pipe;

import lombok.Getter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

public class PipeNode implements PipeContext {

    private static final AtomicLong ids = new AtomicLong();

    @Getter
    private final long id = ids.incrementAndGet();

    private final Pipeline pipeline;

    private final PipeHandler handler;

    private PipeNode pre;

    private PipeNode next;


    public PipeNode(Pipeline pipeline, PipeHandler handler) {
        this.pipeline = pipeline;
        this.handler = handler;
        handler.init(this);
    }


    @Override
    public PipeContext addBefore(PipeHandler handler) {
        PipeNode node = new PipeNode(pipeline, handler);
        link(pre, node, this);
        return node;
    }

    @Override
    public PipeContext addAfter(PipeHandler handler) {
        PipeNode node = new PipeNode(pipeline, handler);
        link(this, node, next);
        return node;
    }

    @Override
    public PipeContext addFirst(PipeHandler handler) {
        return pipeline.addFirst(handler);
    }

    @Override
    public PipeContext addLast(PipeHandler handler) {
        return pipeline.addLast(handler);
    }

    public static void link(PipeNode... nodes) {
        for (int i = 1; i < nodes.length; i++) {
            PipeNode pre = nodes[i - 1];
            PipeNode next = nodes[i];
            if (next != null) {
                next.pre = pre;
            }
            if (pre != null) {
                pre.next = next;
            }
        }
    }

    @Override
    public void fireReceive(ByteBuffer buf) {
        PipeNode node = next;
        if (node != null) {
            node.onReceive(buf);
        }
    }

    @Override
    public void fireWrite(ByteBuffer buf) throws IOException {
        PipeNode node = pre;
        if (node != null) {
            node.onWrite(buf);
        }
    }

    @Override
    public void fireConnected() throws IOException {
        PipeNode node = next;
        if (node != null) {
            node.onConnect();
        }
    }

    @Override
    public void remove() {
        link(pre, next);
        pre = next = null;
    }

    public void onReceive(ByteBuffer buf) {
        handler.onReceive(this, buf);
    }

    public void onWrite(ByteBuffer buf) throws IOException {
        handler.onWrite(this, buf);
    }

    public void onConnect() throws IOException {
        handler.onConnected(this);
    }

}
