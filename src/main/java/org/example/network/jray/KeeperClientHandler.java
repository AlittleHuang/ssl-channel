package org.example.network.jray;

import org.example.log.Logs;
import org.example.network.event.LiteScheduledServices;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.Pipeline;
import org.example.network.pipe.handlers.AuthHandlers;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KeeperClientHandler extends ConnectionKeeper {

    private static final Logger logger = Logs.getLogger(KeeperClientHandler.class);

    public static final int STATUS_KEEP = 0;
    public static final int STATUS_REMOVING = 1;
    public static final int STATUS_REMOVED = 2;

    private final ScheduledExecutorService scheduled;

    private final Object mutex = new Object();

    private PipeContext context;
    private Future<?> scheduledFuture;
    private CompletableFuture<Void> wait = new CompletableFuture<>();
    private long time;
    private final CompletableFuture<Void> removedFuture = new CompletableFuture<>();
    private int status = STATUS_KEEP;

    private final Queue<KeeperClientHandler> deque;

    public KeeperClientHandler(Queue<KeeperClientHandler> deque) {
        this(LiteScheduledServices.getExecutor(), deque);
    }

    public KeeperClientHandler(ScheduledExecutorService scheduled, Queue<KeeperClientHandler> deque) {
        this.scheduled = scheduled;
        this.deque = deque;
    }

    @Override
    public void init(PipeContext ctx) {
        ctx.addBefore(AuthHandlers.client());
        this.context = ctx;
    }

    @Override
    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        try {
            doOnReceive(ctx, buf);
        } finally {
            ctx.free(buf);
        }

    }

    private void doOnReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        if (buf.remaining() != 1) {
            ctx.fireClose();
            return;
        }
        byte read = buf.get();
        if (read != KEEP && read != REMOVE) {
            ctx.fireClose();
            return;
        }
        wait.complete(null);
        logger.log(Level.ALL, () -> "receive " + read + " after " + (System.currentTimeMillis() - time) + " ms");
        if (read == REMOVE) {
            ctx.remove();
            logger.log(Level.DEBUG, "removed");
            status = STATUS_REMOVED;
            removedFuture.complete(null);
        }
    }

    @Override
    public void onConnected(PipeContext ctx) throws IOException {
        wait.complete(null);
        int period = 1000;
        scheduledFuture = scheduled
                .scheduleAtFixedRate(
                        this::keepConnect,
                        new Random().nextInt(period),
                        period,
                        TimeUnit.MILLISECONDS
                );
    }

    @Override
    public void onError(PipeContext ctx, Throwable throwable) {
        super.onError(ctx, throwable);
        removeFromQueue();
    }

    @Override
    public void onClose(PipeContext ctx) throws IOException {
        super.onClose(ctx);
        removeFromQueue();
    }

    private void removeFromQueue() {
        if (deque != null) {
            deque.remove(this);
        }
        if (!scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }
    }

    private void keepConnect() {

        PipeContext ctx = context;
        synchronized (mutex) {
            if (status != STATUS_KEEP) {
                scheduledFuture.cancel(false);
            } else if (wait.isDone()) {
                time = System.currentTimeMillis();
                wait = new CompletableFuture<>();
                try {
                    logger.log(Level.TRACE, "send keep message");
                    ctx.fireWrite(ByteBuffer.wrap(new byte[]{KEEP}));
                } catch (IOException e) {
                    ctx.fireError(e);
                }
            } else {
                removeFromQueue();
                try {
                    context.fireClose();
                } catch (IOException e) {
                    ctx.fireError(e);
                }
            }
        }
    }

    public CompletableFuture<Pipeline> connect() {
        synchronized (mutex) {
            status = STATUS_REMOVING;
            time = System.currentTimeMillis();
            wait.thenAccept(__ -> sendRemoveMessage());
            return removedFuture.thenApplyAsync(__ -> context.pipeline());
        }
    }

    private void sendRemoveMessage() {
        try {
            logger.log(Level.DEBUG, "send remove message");
            context.fireWrite(ByteBuffer.wrap(new byte[]{REMOVE}));
        } catch (IOException e) {
            context.fireError(e);
            removedFuture.completeExceptionally(e);
        }
    }

}
