package org.example.network.jray;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;
import org.example.network.pipe.handlers.AuthHandlers;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

public class KeeperClientHandler implements PipeHandler {

    private final CompletableFuture<Void> connected = new CompletableFuture<>();

    private final Queue<KeeperClientHandler> deque;
    private PipeContext context;

    public KeeperClientHandler(Queue<KeeperClientHandler> deque) {
        this.deque = deque;
    }

    @Override
    public void init(PipeContext ctx) {
        ctx.addBefore(AuthHandlers.client());
        this.context = ctx;
    }

    @Override
    public void onConnected(PipeContext ctx) throws IOException {
        connected.complete(null);
    }

    @Override
    public void onError(PipeContext ctx, Throwable throwable) {
        PipeHandler.super.onError(ctx, throwable);
        removeFromQueue();
    }

    @Override
    public void onClose(PipeContext ctx) throws IOException {
        PipeHandler.super.onClose(ctx);
        removeFromQueue();
    }

    private void removeFromQueue() {
        if (deque != null) {
            deque.remove(this);
        }
    }

    public CompletableFuture<Pipeline> connect() {
        removeFromQueue();
        context.remove();
        return connected.thenApply(__ -> context.pipeline());
    }


}
