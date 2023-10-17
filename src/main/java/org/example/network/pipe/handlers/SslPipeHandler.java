package org.example.network.pipe.handlers;

import org.example.log.Logs;
import org.example.network.buf.Bytes;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import java.io.IOException;
import java.lang.System.Logger;
import java.nio.ByteBuffer;

import static java.lang.System.Logger.Level.TRACE;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.*;
import static javax.net.ssl.SSLEngineResult.Status.*;

public class SslPipeHandler implements PipeHandler {

    private static final Logger logger = Logs
            .getLogger(SslPipeHandler.class);

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
        private boolean handshakeFinished;

        public HandlerImpl(SSLEngine engine) {
            this.engine = engine;
        }


        @Override
        public void onConnected(PipeContext ctx) throws IOException {
            engine.beginHandshake();
            HandshakeStatus status = engine.getHandshakeStatus();
            if (status == NEED_WRAP) {
                wrap(ctx, EMPTY);
            } else if (status == NEED_UNWRAP) {
                unwrap(ctx, EMPTY);
            }
        }

        @Override
        public void onWrite(PipeContext ctx, ByteBuffer buf) throws IOException {
            SSLEngineResult result = wrap(ctx, buf);
            checkHandshaking(ctx, result);
        }

        @Override
        public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
            SSLEngineResult result = unwrap(ctx, buf);
            checkHandshaking(ctx, result);
        }

        private SSLEngineResult unwrap(PipeContext ctx, ByteBuffer buf) throws IOException {
            int size = getAppBufSize();
            SSLEngineResult result;
            while (true) {
                updateUnwrapSrc(ctx, buf);
                ByteBuffer unwrap_dst = ctx.allocate(size);
                result = engine.unwrap(unwrap_src, unwrap_dst);

                Status status = result.getStatus();
                if (result.bytesProduced() > 0) {
                    ctx.fireReceive(unwrap_dst.flip());
                } else {
                    ctx.free(unwrap_dst);
                }
                if (buf.hasRemaining()) {
                    if (status == BUFFER_UNDERFLOW
                        && unwrap_src.remaining() >= getPacketBufSize()) {
                        enlargePacketBufSize();
                    }
                } else if (status == BUFFER_UNDERFLOW) {
                    break;
                } else if (status == BUFFER_OVERFLOW) {
                    enlargeAppBufSize();
                    size = getAppBufSize();
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
            ctx.free(buf);
            if (!unwrap_src.hasRemaining()) {
                ctx.free(unwrap_src);
                this.unwrap_src = null;
            }
            if (result.getHandshakeStatus() == FINISHED) {
                handshakeFinished(ctx);
            }
            SSLEngineResult r = result;
            logger.log(TRACE, () -> "unwrap: " + r);
            return result;
        }


        private void updateUnwrapSrc(PipeContext ctx, ByteBuffer receive) {
            if (unwrap_src == null) {
                unwrap_src = ctx.allocate(getPacketBufSize());
            } else {
                unwrap_src.compact();
            }
            int len = Math.min(unwrap_src.limit() - unwrap_src.position(), receive.remaining());
            unwrap_src.put(unwrap_src.position(), receive, receive.position(), len);
            unwrap_src.position(unwrap_src.position() + len);
            receive.position(receive.position() + len);
            unwrap_src.flip();
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


        private SSLEngineResult wrap(PipeContext ctx, ByteBuffer wrap_src) throws IOException {
            int wrap_dst_size = getPacketBufSize();
            ByteBuffer wrap_dst = ctx.allocate(wrap_dst_size);
            SSLEngineResult result = engine.wrap(wrap_src, wrap_dst);
            if (wrap_dst.flip().hasRemaining()) {
                ctx.fireWrite(wrap_dst);
            }
            Status status = result.getStatus();
            if (status == BUFFER_OVERFLOW) {
                enlargePacketBufSize();
                return wrap(ctx, wrap_src);
            } else if (status == CLOSED) {
                engine.closeOutbound();
            }
            if (result.getHandshakeStatus() == FINISHED) {
                handshakeFinished(ctx);
            }
            logger.log(TRACE, () -> "wrap: " + result);
            return result;
        }

        private void handshakeFinished(PipeContext ctx) throws IOException {
            if (!handshakeFinished) {
                handshakeFinished = true;
                logger.log(TRACE, "handshake finished");
                ctx.fireConnected();
            }
        }

        private void enlargePacketBufSize() {
            packet_buf_size = getPacketBufSize() * 2;
        }

        private void enlargeAppBufSize() {
            app_buf_size = getAppBufSize() * 2;
        }

        private int getAppBufSize() {
            if (app_buf_size == 0) {
                int size = engine.getSession().getApplicationBufferSize();
                return app_buf_size = Bytes.sizeFor(size);
            }
            return app_buf_size;
        }

        private int getPacketBufSize() {
            if (packet_buf_size == 0) {
                int size = engine.getSession().getPacketBufferSize();
                packet_buf_size = Bytes.sizeFor(size);
            }
            return packet_buf_size;
        }
    }


}
