package org.example.network;


import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.Pipe.SourceChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SslClientChannel {

    private final ByteBufferAllocator allocator;
    private final SocketChannel channel;
    private final SSLEngine engine;
    private final Object wrapLock = new Object(), unwrapLock = new Object();
    private ByteBuffer unwrap_src, wrap_dst;
    private final Pipe wrap_src_pipe, unwrap_dst_pipe;
    private boolean engineClosed = false;
    private boolean channelClosed = false;

    private int app_buf_size;
    private int packet_buf_size;
    private int totalResult = 0;

    Lock handshaking = new ReentrantLock();

    private boolean handshakingFinished;

    public SslClientChannel(ByteBufferAllocator allocator, SocketChannel channel, SSLEngine engine) {
        this.allocator = allocator;
        this.channel = channel;
        this.engine = engine;
        this.unwrap_src = allocate(BufType.PACKET);
        this.wrap_dst = allocate(BufType.PACKET);
        this.wrap_src_pipe = openPipe();
        this.unwrap_dst_pipe = openPipe();
    }

    private static Pipe openPipe() {
        try {
            Pipe open = Pipe.open();
            // noinspection resource
            open.source().configureBlocking(false);
            return open;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(ByteBuffer wrap) throws IOException {
        checkHandshaking();
        // noinspection resource
        wrap_src_pipe.sink().write(wrap);
        wrapAndWrite();
    }

    private void wrapAndWrite() throws IOException {
        doWrapAndSend();
    }

    private SSLEngineResult doWrapAndSend() throws IOException {
        if (engineClosed) {
            throw new IOException("Engine is closed");
        }
        Status status;
        SSLEngineResult result;
        ByteBuffer wrap_src = allocate(BufType.APPLICATION);
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
                    System.out.println("write:" + write);
                    l -= write;
                }
            }
        }
        return result;
    }

    public int read(ByteBuffer buffer) throws IOException {
        if (!isClosed()) {
            checkHandshaking();
            receiveAndUnwrap();
        }
        SourceChannel source = unwrap_dst_pipe.source();
        int read = source.read(buffer);
        if (isClosed() && read == 0) {
            read = -1;
        }
        return read;
    }

    private boolean isClosed() {
        return channelClosed && engineClosed;
    }

    private SSLEngineResult receiveAndUnwrap() throws IOException {
        Status status;
        SSLEngineResult result;
        ByteBuffer buf = allocate(BufType.APPLICATION);
        if (engineClosed) {
            throw new IOException("Engine is closed");
        }
        synchronized (unwrapLock) {
            do {
                if (!channelClosed) {
                    int read;
                    if ((read = channel.read(unwrap_src)) == -1) {
                        channelClosed = true;
                    }
                    if (read != 0) {
                        System.out.println("read:" + read);
                    }
                }
                unwrap_src.flip();
                result = engine.unwrap(unwrap_src, buf);
                while (buf.hasRemaining()) {
                    buf.flip();
                    int remaining = buf.remaining();
                    while (remaining > 0) {
                        // noinspection resource
                        int write = unwrap_dst_pipe.sink().write(buf);
                        remaining -= write;
                        totalResult += write;
                    }
                }
                buf.clear();
                status = result.getStatus();

                if (status == Status.BUFFER_UNDERFLOW) {
                    if (unwrap_src.limit() == unwrap_src.capacity()) {
                        unwrap_src = realloc(unwrap_src, false, BufType.PACKET);
                    }
                } else if (status == Status.BUFFER_OVERFLOW) {
                    buf = realloc(buf, true, BufType.APPLICATION);
                } else if (status == Status.CLOSED) {
                    engineClosed = true;
                    return result;
                }
                unwrap_src.compact();
            } while (status != Status.OK || channelClosed && unwrap_src.position() != unwrap_src.capacity());
        }

        if (result.getHandshakeStatus() == HandshakeStatus.FINISHED) {
            this.handshakingFinished = true;
            System.out.println("handshake success");
            wrapAndWrite();
        }

        if (isClosed()) {
            wrap_src_pipe.sink().close();
        }

        return result;
    }

    private ByteBuffer realloc(ByteBuffer b, boolean flip, BufType type) {
        synchronized (this) {
            int nsz = 2 * b.capacity();
            ByteBuffer n = allocate(type, nsz);
            if (flip) {
                b.flip();
            }
            n.put(b);
            b = n;
        }
        return b;
    }

    private void checkHandshaking() throws IOException {
        HandshakeStatus hs_status = engine.getHandshakeStatus();
        try {
            handshaking.lock();
            SSLEngineResult result = null;
            while (hs_status != HandshakeStatus.FINISHED &&
                   hs_status != HandshakeStatus.NOT_HANDSHAKING) {
                switch (hs_status) {
                    case NEED_TASK:
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            task.run();
                        }
                        /* fall thru - call wrap again */
                    case NEED_WRAP:
                        result = doWrapAndSend();
                        break;

                    case NEED_UNWRAP:
                        result = receiveAndUnwrap();
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


    private ByteBuffer allocate(BufType type) {
        return allocate(type, -1);
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
            return allocator.allocate(size);
        }
    }

    public int getTotalResult() {
        return totalResult;
    }
}
