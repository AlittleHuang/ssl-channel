package org.example.network.channel.pipe.handlers;

import org.example.network.channel.pipe.PipeContext;
import org.example.network.channel.pipe.PipeHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import java.io.IOException;
import java.nio.ByteBuffer;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
import static javax.net.ssl.SSLEngineResult.Status.*;

public class SslPipeHandler implements PipeHandler {

    private static final ByteBuffer EMPTY = ByteBuffer.wrap(new byte[0]);

    private final SSLContext sslContext;
    private final boolean useClientMode;

    public SslPipeHandler(SSLContext sslContext, boolean useClientMode) {
        this.sslContext = sslContext;
        this.useClientMode = useClientMode;
    }

    @Override
    public void init(PipeContext ctx) {
        try {
            initHandler(ctx);
        } finally {
            ctx.remove();
        }
    }

    private void initHandler(PipeContext ctx) {
        SSLEngine engine = sslContext.createSSLEngine();
        HandlerImpl handler = new HandlerImpl(engine);
        engine.setUseClientMode(useClientMode);
        ctx.replace(handler);
    }

    static class HandlerImpl implements PipeHandler {

        private final SSLEngine engine;
        private ByteBuffer unwrap_src;
        private int app_buf_size;
        private int packet_buf_size;

        public HandlerImpl(SSLEngine engine) {
            this.engine = engine;
        }

        @Override
        public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
            SSLEngineResult result = unwrap(ctx, buf);
            checkHandshaking(ctx, result);
        }

        @Override
        public void onConnected(PipeContext ctx) throws IOException {
            wrap(ctx, EMPTY);
            unwrap(ctx, EMPTY);
        }

        private ByteBuffer mergeUnwrapSrc(PipeContext ctx, ByteBuffer receive) {
            if (unwrap_src == null) {
                return receive;
            }
            if (!receive.hasRemaining()) {
                ctx.free(receive);
                return unwrap_src;
            }
            ByteBuffer dst;
            int minSize = unwrap_src.remaining() + receive.remaining();
            int size = getPacketBufSize();
            while (size < minSize) {
                size = enlargePacketBufSize();
            }
            dst = ctx.allocate(size);
            dst.put(unwrap_src);
            dst.put(receive);
            return dst.flip();
        }

        private SSLEngineResult unwrap(PipeContext ctx, ByteBuffer buf) throws IOException {
            ByteBuffer unwrap_src = mergeUnwrapSrc(ctx, buf);
            int size = getAppBufSize();
            SSLEngineResult result;
            while (true) {
                ByteBuffer unwrap_dst = ctx.allocate(size);
                result = engine.unwrap(unwrap_src, unwrap_dst);

                Status status = result.getStatus();
                if (result.bytesProduced() > 0) {
                    ctx.fireReceive(unwrap_dst.flip());
                }
                if (status == BUFFER_UNDERFLOW) {
                    break;
                } else if (status == BUFFER_OVERFLOW) {
                    size = enlargeApplicationBufSize();
                } else if (status == CLOSED) {
                    engine.closeInbound();
                    break;
                } else if (result.bytesProduced() == 0 && result.bytesConsumed() == 0) {
                    break;
                } else if (status == Status.OK
                           && result.getHandshakeStatus() != NOT_HANDSHAKING) {
                    break;
                }
            }
            if (unwrap_src.hasRemaining()) {
                this.unwrap_src = unwrap_src;
            } else {
                this.unwrap_src = null;
                ctx.free(unwrap_src);
            }
            if (result.getHandshakeStatus() == FINISHED) {
                ctx.fireConnected();
            }
            return result;
        }

        private void checkHandshaking(PipeContext ctx, SSLEngineResult result) throws IOException {
            HandshakeStatus hs_status = result.getHandshakeStatus();
            while (hs_status != FINISHED &&
                   hs_status != NOT_HANDSHAKING) {
                switch (hs_status) {
                    case NEED_TASK:
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            task.run();
                        }
                        hs_status = engine.getHandshakeStatus();
                        continue;
                    case NEED_WRAP:
                        result = wrap(ctx, EMPTY);
                        break;
                    case NEED_UNWRAP:
                        result = unwrap(ctx, EMPTY);
                        break;
                }
                hs_status = result.getHandshakeStatus();
                if (result.bytesProduced() == 0 && result.bytesConsumed() == 0) {
                    break;
                }
            }
        }

        @Override
        public void onWrite(PipeContext ctx, ByteBuffer buf) throws IOException {
            SSLEngineResult result = wrap(ctx, buf);
            checkHandshaking(ctx, result);
        }

        private SSLEngineResult wrap(PipeContext ctx, ByteBuffer wrap_src) throws IOException {
            int wrap_dst_size = getPacketBufSize();
            ByteBuffer wrap_dst = ctx.allocate(wrap_dst_size);
            SSLEngineResult result = engine.wrap(wrap_src, wrap_dst);
            if (wrap_dst.flip().hasRemaining()) {
                ctx.fireWrite(wrap_dst);
            }
            if (result.getStatus() == CLOSED) {
                engine.closeOutbound();
            }
            if (result.getHandshakeStatus() == FINISHED) {
                ctx.fireConnected();
            }
            return result;
        }

        private int enlargePacketBufSize() {
            return (packet_buf_size = getPacketBufSize() * 2);
        }

        private int enlargeApplicationBufSize() {
            return (app_buf_size = getAppBufSize() * 2);
        }

        private int getAppBufSize() {
            return app_buf_size == 0
                    ? (app_buf_size = engine.getSession().getApplicationBufferSize())
                    : app_buf_size;
        }

        private int getPacketBufSize() {
            return packet_buf_size == 0
                    ? (packet_buf_size = engine.getSession().getPacketBufferSize())
                    : packet_buf_size;
        }
    }


}
