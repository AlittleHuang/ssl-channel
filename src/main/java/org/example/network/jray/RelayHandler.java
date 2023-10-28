package org.example.network.jray;

import org.example.network.pipe.HttpProxyHeaderReader;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;
import org.example.network.pipe.handlers.HttpProxyServerHandler.ProxyRequest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public class RelayHandler implements PipeHandler {

    private final Pipeline inbound;
    private final HttpProxyHeaderReader requestReader;

    private Pipeline outbound;
    private boolean inboundConnected;
    private boolean outboundConnected;

    private boolean connectionEstablished;
    private ProxyRequest request;


    public RelayHandler(Pipeline inbound, ClientConnectionPool connectionPool) throws IOException {
        this.inbound = inbound;
        this.requestReader = new HttpProxyHeaderReader(inbound);
        try {
            CompletableFuture<Pipeline> pipelineFuture = connectionPool.get();
            pipelineFuture.thenAccept(this::onRemoteConnected);
        } catch (InterruptedException e) {
            inbound.onError(e);
        }
    }

    private void onRemoteConnected(Pipeline remote) {
        this.outbound = remote;
        remote.addLast(new PipeHandler() {

            @Override
            public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
                if (inbound.isClosed()) {
                    try {
                        ctx.fireClose();
                    } finally {
                        ctx.free(buf);
                    }
                } else {
                    inbound.write(buf);
                }
            }

            @Override
            public void onClose(PipeContext ctx) throws IOException {
                ctx.fireClose();
                inbound.close();
            }

            @Override
            public void onWrite(PipeContext ctx, ByteBuffer buf) throws IOException {
                PipeHandler.super.onWrite(ctx, buf);
            }
        });
        outboundConnected = true;
        try {
            sendTargetAddress();
        } catch (IOException e) {
            remote.onError(e);
        }
        updateAutoRead();

    }

    @Override
    public void onConnected(PipeContext ctx) throws IOException {
        ctx.pipeline().setAutoRead(true);
    }

    private void updateAutoRead() {
        boolean readable = inboundConnected && outboundConnected;
        inbound.setAutoRead(readable);
        if (outbound != null) {
            outbound.setAutoRead(readable);
        }
    }

    @Override
    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        if (connectionEstablished) {
            if (outbound.isClosed()) {
                ctx.fireClose();
                ctx.free(buf);
            } else {
                outbound.write(buf);
            }
        } else {
            if (requestReader.update(buf)) {
                this.request = requestReader.getRequest();
                this.connectionEstablished = true;
                if ("CONNECT".equals(request.method())) {
                    ctx.pipeline().setAutoRead(false);
                    byte[] bytes = "HTTP/1.1 200 Connection established\r\n\r\n".getBytes();
                    ByteBuffer bf = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
                    ctx.fireWrite(bf);
                }
                sendTargetAddress();

                ctx.fireConnected();
                inboundConnected = true;
                updateAutoRead();
            }
        }
    }

    private void sendTargetAddress() throws IOException {
        if (outboundConnected && request != null) {
            ProxyRequest request = this.request;
            this.request = null;
            String targetAddress = request.host() + ":" + request.port() + "\n";
            outbound.write(ByteBuffer.wrap(targetAddress.getBytes()));
            if (!"CONNECT".equals(request.method())) {
                outbound.write(request.forward());
            }
        }
    }

    @Override
    public void onClose(PipeContext ctx) throws IOException {
        ctx.fireClose();
        outbound.close();
    }
}
