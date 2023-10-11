package org.example.network.channel.handler;


import org.example.network.channel.SelectorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class SslChannelHandler implements ChannelHandler {

    private static final Logger logger = Logger
            .getLogger(SslChannelHandler.class.getName());

    private final SocketChannel channel;
    private final SSLEngine engine;
    private final Object wrapLock = new Object(), unwrapLock = new Object();
    private final int ops;
    private ByteBuffer unwrap_src;
    private final Pipe wrap_src_pipe, unwrap_dst_pipe;
    private boolean engineClosed = false;
    private boolean channelClosed = false;

    private int app_buf_size;
    private int packet_buf_size;

    private final SelectorKeyHandler handler;

    Lock handshaking = new ReentrantLock();

    private boolean handshakingFinished;
    private SelectorService service;

    public static SslChannelHandler clientChannel(SocketAddress address,
                                                  SelectorKeyHandler handler)
            throws IOException {
        SSLEngine sslEngine;
        try {
            sslEngine = SSLContext.getDefault().createSSLEngine();
            sslEngine.setUseClientMode(true);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        SocketChannel ch = SocketChannel.open();
        ch.configureBlocking(false);
        ch.connect(address);
        int ops = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
        return new SslChannelHandler(sslEngine, ch, ops, handler);
    }

    public SslChannelHandler(SSLEngine engine,
                             SocketChannel channel,
                             int ops,
                             SelectorKeyHandler handler) {
        this.channel = channel;
        this.engine = engine;
        this.handler = handler;
        this.wrap_src_pipe = openNoneBolckingPipe();
        this.unwrap_dst_pipe = openNoneBolckingPipe();
        this.ops = ops;
    }


    @Override
    public void init(SelectorService service) throws IOException {
        this.service = service;
        this.unwrap_src = allocate(BufType.PACKET);
        register(wrap_src_pipe.sink(), ops, handler);
        register(unwrap_dst_pipe.source(), ops, handler);
    }

    @Override
    public SelectableChannel channel() {
        return channel;
    }

    @Override
    public int registerOps() {
        return channel.validOps();
    }

    @Override
    public void handler(SelectionKey key) throws IOException {
        try {
            doHandler(key);
        } catch (Exception e) {
            channel.close();
            closePipes();
            throw e;
        }
    }

    private void doHandler(SelectionKey key) throws IOException {
        if (key.isConnectable()) {
            channel.finishConnect();
        } else if (key.isReadable()) {
            checkHandshaking();
            receiveAndUnwrap();
        } else if (key.isWritable()) {
            checkHandshaking();
            wrapAndWrite();
        }
    }

    @SuppressWarnings("resource")
    private static Pipe openNoneBolckingPipe() {
        try {
            Pipe open = Pipe.open();
            open.source().configureBlocking(false);
            open.sink().configureBlocking(false);
            return open;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void wrapAndWrite() throws IOException {
        if (!isClosed()) {
            doWrapAndSend();
        }
    }

    private SSLEngineResult doWrapAndSend() throws IOException {
        if (engineClosed) {
            throw new IOException("Engine is closed");
        }
        Status status;
        SSLEngineResult result;
        ByteBuffer wrap_src = allocate(BufType.APPLICATION);
        ByteBuffer wrap_dst = allocate(BufType.PACKET);
        synchronized (wrapLock) {
            wrap_dst.clear();
            do {
                if (handshakingFinished) {
                    // noinspection resource
                    wrap_src_pipe.source().read(wrap_src);
                }
                wrap_src.flip();
                result = engine.wrap(wrap_src, wrap_dst);
                status = result.getStatus();
                if (status == Status.BUFFER_OVERFLOW) {
                    wrap_dst = realloc(wrap_dst, true, BufType.PACKET);
                }
            } while (status == Status.BUFFER_OVERFLOW);
            if (status == Status.CLOSED) {
                engineClosed = true;
                return result;
            }
            if (result.bytesProduced() > 0) {
                wrap_dst.flip();
                int l = wrap_dst.remaining();
                assert l == result.bytesProduced();
                while (l > 0) {
                    int write = channel.write(wrap_dst);
                    logger.fine(() -> "write: " + write + "bytes");
                    l -= write;
                }
            }
        }
        free(wrap_src);
        free(wrap_dst);
        return result;
    }

    private boolean isClosed() {
        return channelClosed && engineClosed;
    }


    private void receiveAndUnwrap() throws IOException {
        if (!isClosed()) {
            doReceiveAndUnwrap();
        }
    }

    private SSLEngineResult doReceiveAndUnwrap() throws IOException {
        if (engineClosed) {
            throw new IOException("Engine is closed");
        }
        Status status;
        SSLEngineResult result;
        ByteBuffer unwrap_dst = allocate(BufType.APPLICATION);
        synchronized (unwrapLock) {
            do {
                if (!channelClosed) {
                    int read;
                    if ((read = channel.read(unwrap_src)) == -1) {
                        channelClosed = true;
                    }
                    if (read != 0) {
                        logger.fine(() -> "read:" + read + "bytes");
                    }
                }
                unwrap_src.flip();
                result = engine.unwrap(unwrap_src, unwrap_dst);
                while (unwrap_dst.hasRemaining()) {
                    unwrap_dst.flip();
                    int remaining = unwrap_dst.remaining();
                    while (remaining > 0) {
                        // noinspection resource
                        int write = unwrap_dst_pipe.sink().write(unwrap_dst);
                        remaining -= write;
                    }
                }
                unwrap_dst.clear();
                status = result.getStatus();

                if (status == Status.BUFFER_UNDERFLOW) {
                    if (unwrap_src.limit() == unwrap_src.capacity()) {
                        unwrap_src = realloc(unwrap_src, false, BufType.PACKET);
                    }
                } else if (status == Status.BUFFER_OVERFLOW) {
                    unwrap_dst = realloc(unwrap_dst, true, BufType.APPLICATION);
                } else if (status == Status.CLOSED) {
                    engineClosed = true;
                    break;
                }
                unwrap_src.compact();
            } while (channelClosed ? unwrap_src.position() > 0 : status != Status.OK);
        }
        free(unwrap_dst);
        if (result.getHandshakeStatus() == HandshakeStatus.FINISHED) {
            this.handshakingFinished = true;
            logger.fine(() -> "handshake success");
            wrapAndWrite();
        }

        if (isClosed()) {
            closePipes();
        }

        return result;
    }


    private void checkHandshaking() throws IOException {
        if (handshakingFinished || !channel.isConnected()) {
            return;
        }
        HandshakeStatus hs_status = engine.getHandshakeStatus();
        try {
            handshaking.lock();
            wrapAndWrite();
            SSLEngineResult result = null;
            while (hs_status != HandshakeStatus.FINISHED &&
                   hs_status != HandshakeStatus.NOT_HANDSHAKING) {
                switch (hs_status) {
                    case NEED_TASK:
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            task.run();
                        }
                    case NEED_WRAP:
                        result = doWrapAndSend();
                        break;

                    case NEED_UNWRAP:
                        result = doReceiveAndUnwrap();
                        break;
                }
                if (result != null) {
                    hs_status = result.getHandshakeStatus();
                }
            }
        } finally {
            handshaking.unlock();
        }
    }


    enum BufType {
        PACKET, APPLICATION
    }

    private void free(ByteBuffer wrap_src) {
        service.getAllocator().free(wrap_src);
    }

    private ByteBuffer allocate(BufType type) {
        return allocate(type, -1);
    }


    private ByteBuffer realloc(ByteBuffer b, boolean flip, BufType type) {
        synchronized (this) {
            int nsz = 2 * b.capacity();
            ByteBuffer n = allocate(type, nsz);
            if (flip) {
                b.flip();
            }
            n.put(b);
            free(b);
            return n;
        }
    }

    private ByteBuffer allocate(BufType type, int len) {
        assert engine != null;
        synchronized (this) {
            int size;
            if (type == BufType.PACKET) {
                if (packet_buf_size == 0) {
                    SSLSession sess = engine.getSession();
                    packet_buf_size = sess.getPacketBufferSize();
                }
                if (len > packet_buf_size) {
                    packet_buf_size = len;
                }
                size = packet_buf_size;
            } else {
                if (app_buf_size == 0) {
                    SSLSession sess = engine.getSession();
                    app_buf_size = sess.getApplicationBufferSize();
                }
                if (len > app_buf_size) {
                    app_buf_size = len;
                }
                size = app_buf_size;
            }
            return service.getAllocator().allocate(size);
        }
    }

    private void register(SelectableChannel ch,
                          int ops,
                          SelectorKeyHandler handler)
            throws IOException {
        service.register(ch, ops & ch.validOps(), handler);
    }

    private void closePipes() throws IOException {
        close(unwrap_dst_pipe);
        close(wrap_src_pipe);
    }

    private void close(Pipe pipe) throws IOException {
        handlerAndClose(pipe.sink());
        handlerAndClose(pipe.source());
    }

    private void handlerAndClose(SelectableChannel channel) throws IOException {
        SelectionKey key = channel.keyFor(service.getSelector());
        if (key != null) {
            handler.handler(key);
        }
        channel.close();
    }


}
