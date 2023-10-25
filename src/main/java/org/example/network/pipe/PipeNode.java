package org.example.network.pipe;


import org.example.network.event.NioEventLoopExecutor;
import org.example.network.pipe.handlers.HandlerUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import static org.example.network.pipe.handlers.HandlerUtil.MethodDeclaring.*;

public class PipeNode implements PipeContext {

    private static final AtomicLong ids = new AtomicLong();

    private final long id = ids.incrementAndGet();

    private final Pipeline pipeline;

    private final PipeHandler handler;

    private PipeNode pre;

    private PipeNode next;


    public PipeNode(Pipeline pipeline, PipeHandler handler) {
        this.pipeline = pipeline;
        this.handler = handler;
    }

    public void init() {
        handler.init(this);
    }

    @Override
    public PipeContext addBefore(PipeHandler handler) {
        PipeNode node = new PipeNode(pipeline, handler);
        link(pre, node, this);
        node.init();
        return node;
    }

    @Override
    public PipeContext addAfter(PipeHandler handler) {
        PipeNode node = new PipeNode(pipeline, handler);
        link(this, node, next);
        node.init();
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


    @Override
    public void remove() {
        link(pre, next);
        pre = next = null;
    }

    @Override
    public void replace(PipeHandler handler) {
        PipeNode node = new PipeNode(pipeline, handler);
        link(pre, node, next);
        node.init();
        pre = next = null;
    }


    @Override
    public Pipeline pipeline() {
        return pipeline;
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
    public void fireReceive(ByteBuffer buf) throws IOException {
        PipeNode node = next;
        while (node != null && HandlerUtil.isDefault(ON_RECEIVE, node.handler)) {
            node = node.next;
        }
        if (node != null) {
            node.onReceive(buf);
        }
    }

    @Override
    public void fireWrite(ByteBuffer buf) throws IOException {
        PipeNode node = pre;
        while (node != null && HandlerUtil.isDefault(ON_WRITE, node.handler)) {
            node = node.pre;
        }
        if (node != null) {
            node.onWrite(buf);
        }
    }

    @Override
    public void fireConnected() throws IOException {
        PipeNode node = next;
        while (node != null && HandlerUtil.isDefault(ON_CONNECTED, node.handler)) {
            node = node.next;
        }
        if (node != null) {
            node.onConnected();
        }
    }

    @Override
    public void fireClose() throws IOException {
        PipeNode node = pre;
        while (node != null && HandlerUtil.isDefault(ON_CLOSE, node.handler)) {
            node = node.pre;
        }
        if (node != null) {
            node.onClose();
        }
    }

    @Override
    public void fireError(Throwable throwable) {
        PipeNode node = pre;
        while (node != null && HandlerUtil.isDefault(ON_ERROR, node.handler)) {
            node = node.pre;
        }
        if (node != null) {
            node.onError(throwable);
        }
    }

    @Override
    public void fireConnect(InetSocketAddress address) throws IOException {
        PipeNode node = pre;
        while (node != null && HandlerUtil.isDefault(ON_CONNECT, node.handler)) {
            node = node.pre;
        }
        if (node != null) {
            node.onConnect(address);
        }
    }

    @Override
    public void fireReadeTheEnd() throws IOException {
        PipeNode node = next;
        while (node != null && HandlerUtil.isDefault(ON_READ_THE_END, node.handler)) {
            node = node.next;
        }
        if (node != null) {
            node.onReadTheEnd();
        }
    }

    private void onReadTheEnd() throws IOException {
        handler.onReadTheEnd(this);
    }

    private void onConnect(InetSocketAddress address) throws IOException {
        handler.onConnect(this, address);
    }

    @Override
    public NioEventLoopExecutor executor() {
        return pipeline.executor();
    }

    private void onClose() throws IOException {
        handler.onClose(this);
    }

    @Override
    public long getId() {
        return id;
    }

    public void onReceive(ByteBuffer buf) throws IOException {
        handler.onReceive(this, buf);
    }

    public void onWrite(ByteBuffer buf) throws IOException {
        handler.onWrite(this, buf);
    }

    public void onConnected() throws IOException {
        handler.onConnected(this);
    }

    public void onError(Throwable throwable) {
        handler.onError(this, throwable);
    }
}
