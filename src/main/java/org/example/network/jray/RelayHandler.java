package org.example.network.jray;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class RelayHandler implements PipeHandler {

    private Pipeline remote;
    private final Pipeline local;

    public RelayHandler(Pipeline local, ClientConnectionPool connectionPool) throws IOException {
        this.local = local;
        try {
            CompletableFuture<Pipeline> pipelineFuture = connectionPool.get();
            pipelineFuture.thenAccept(this::onRemoteConnected);
        } catch (InterruptedException e) {
            local.onError(e);
        }
    }

    private void onRemoteConnected(Pipeline remote) {
        this.remote = remote;
        remote.addLast(new PipeHandler() {

            @Override
            public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
                if (local.isClosed()) {
                    ctx.fireClose();
                    ctx.free(buf);
                } else {
                    local.write(buf);
                }
            }

            @Override
            public void onClose(PipeContext ctx) throws IOException {
                ctx.fireClose();
                local.close();
            }
        });

        local.setAutoRead(true);

    }

    @Override
    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        if (remote.isClosed()) {
            ctx.fireClose();
            ctx.free(buf);
        } else {
            remote.write(buf);
        }
    }

    @Override
    public void onClose(PipeContext ctx) throws IOException {
        ctx.fireClose();
        remote.close();
    }
}
