package org.example.network;


import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SslPipe {

    private final BufferAllocator allocator;
    private final SocketChannel chan;
    private final SSLEngine engine;
    private final Object wrapLock = new Object(), unwrapLock = new Object();
    ByteBuffer unwrap_src, wrap_dst;
    boolean closed = false;
    int u_remaining; // the number of bytes left in unwrap_src after an unwrap()

    int app_buf_size;
    int packet_buf_size;

    Lock handshaking = new ReentrantLock();

    public SslPipe(BufferAllocator allocator, SocketChannel chan, SSLEngine engine) {
        this.allocator = allocator;
        this.chan = chan;
        this.engine = engine;
        this.unwrap_src = allocate(BufType.PACKET);
        this.wrap_dst = allocate(BufType.PACKET);

    }

    SSLEngineResult wrapAndSend(ByteBuffer src) throws IOException {
        return wrapAndSendX(src, false);
    }

    SSLEngineResult wrapAndSendX(ByteBuffer src, boolean ignoreClose) throws IOException {
        if (closed && !ignoreClose) {
            throw new IOException("Engine is closed");
        }
        Status status;
        SSLEngineResult result;
        synchronized (wrapLock) {
            wrap_dst.clear();
            do {
                result = engine.wrap(src, wrap_dst);
                status = result.getStatus();
                if (status == Status.BUFFER_OVERFLOW) {
                    ByteBuffer free = wrap_dst;
                    wrap_dst = realloc(free, true, BufType.PACKET);
                    allocator.free(wrap_dst);
                }
            } while (status == Status.BUFFER_OVERFLOW);
            if (status == Status.CLOSED && !ignoreClose) {
                closed = true;
                return result;
            }
            if (result.bytesProduced() > 0) {
                wrap_dst.flip();
                int l = wrap_dst.remaining();
                assert l == result.bytesProduced();
                System.out.println("write:\n\n" + ByteBufferUtil.toBase64(wrap_dst) + "\n\n");
                while (l > 0) {
                    l -= chan.write(wrap_dst);
                }
            }
        }
        return result;
    }

    private ByteBuffer realloc(ByteBuffer b, boolean flip, BufType type) {
        synchronized (this) {
            int nsize = 2 * b.capacity();
            ByteBuffer n = allocate(type, nsize);
            if (flip) {
                b.flip();
            }
            n.put(b);
            b = n;
        }
        return b;
    }

    /* block until a complete message is available and return it
     * in dst, together with the Result. dst may have been re-allocated
     * so caller should check the returned value in Result
     * If handshaking is in progress then, possibly no data is returned
     */
    WrapperResult receiveAndUnwrap(ByteBuffer dst) throws IOException {
        Status status = Status.OK;
        WrapperResult r = new WrapperResult();
        r.buf = dst;
        if (closed) {
            throw new IOException("Engine is closed");
        }
        boolean needData;
        if (u_remaining > 0) {
            unwrap_src.compact();
            unwrap_src.flip();
            needData = false;
        } else {
            unwrap_src.clear();
            needData = true;
        }
        synchronized (unwrapLock) {
            int x;
            do {
                if (needData) {
                    // do {
                        x = chan.read(unwrap_src);
                    // } while (x == 0);
                    if (x == -1) {
                        throw new IOException("connection closed for reading");
                    }
                    unwrap_src.flip();
                    System.out.println("read:\n\n" + ByteBufferUtil.toBase64(unwrap_src) + "\n\n");
                }
                r.result = engine.unwrap(unwrap_src, r.buf);
                status = r.result.getStatus();
                if (status == Status.BUFFER_UNDERFLOW) {
                    if (unwrap_src.limit() == unwrap_src.capacity()) {
                        /* buffer not big enough */
                        unwrap_src = realloc(
                                unwrap_src, false, BufType.PACKET
                        );
                    } else {
                        /* Buffer not full, just need to read more
                         * data off the channel. Reset pointers
                         * for reading off SocketChannel
                         */
                        unwrap_src.position(unwrap_src.limit());
                        unwrap_src.limit(unwrap_src.capacity());
                    }
                    needData = true;
                } else if (status == Status.BUFFER_OVERFLOW) {
                    r.buf = realloc(r.buf, true, BufType.APPLICATION);
                    needData = false;
                } else if (status == Status.CLOSED) {
                    closed = true;
                    r.buf.flip();
                    return r;
                }
            } while (status != Status.OK);
        }
        u_remaining = unwrap_src.remaining();
        return r;
    }

    /**
     * send the data in the given ByteBuffer. If a handshake is needed
     * then this is handled within this method. When this call returns,
     * all of the given user data has been sent and any handshake has been
     * completed. Caller should check if engine has been closed.
     */
    public SSLEngineResult sendData(ByteBuffer src) throws IOException {
        SSLEngineResult r = null;
        while (src.remaining() > 0) {
            r = wrapAndSend(src);
            Status status = r.getStatus();
            if (status == Status.CLOSED) {
                doClosure();
                return r;
            }
            HandshakeStatus hs_status = r.getHandshakeStatus();
            if (hs_status != HandshakeStatus.FINISHED &&
                hs_status != HandshakeStatus.NOT_HANDSHAKING) {
                doHandshake(hs_status);
            }
        }
        return r;
    }

    /**
     * read data thru the engine into the given ByteBuffer. If the
     * given buffer was not large enough, a new one is allocated
     * and returned. This call handles handshaking automatically.
     * Caller should check if engine has been closed.
     */
    public WrapperResult receiveData(ByteBuffer dst) throws IOException {
        /* we wait until some user data arrives */
        WrapperResult r = null;
        assert dst.position() == 0;
        while (dst.position() == 0) {
            r = receiveAndUnwrap(dst);
            dst = (r.buf != dst) ? r.buf : dst;
            Status status = r.result.getStatus();
            if (status == Status.CLOSED) {
                doClosure();
                return r;
            }

            HandshakeStatus hs_status = r.result.getHandshakeStatus();
            if (hs_status != HandshakeStatus.FINISHED &&
                hs_status != HandshakeStatus.NOT_HANDSHAKING) {
                doHandshake(hs_status);
            }
        }
        dst.flip();
        return r;
    }

    /* we've received a close notify. Need to call wrap to send
     * the response
     */
    void doClosure() throws IOException {
        ByteBuffer tmp = null;
        try {
            tmp = allocate(BufType.APPLICATION);
            handshaking.lock();
            SSLEngineResult r;
            Status st;
            HandshakeStatus hs;
            do {
                tmp.clear();
                tmp.flip();
                r = wrapAndSendX(tmp, true);
                hs = r.getHandshakeStatus();
                st = r.getStatus();
            } while (st != Status.CLOSED &&
                     !(st == Status.OK && hs == HandshakeStatus.NOT_HANDSHAKING));
        } finally {
            allocator.free(tmp);
            handshaking.unlock();
        }
    }

    /* do the (complete) handshake after acquiring the handshake lock.
     * If two threads call this at the same time, then we depend
     * on the wrapper methods being idempotent. eg. if wrapAndSend()
     * is called with no data to send then there must be no problem
     */
    @SuppressWarnings("fallthrough")
    void doHandshake(HandshakeStatus hs_status) throws IOException {
        ByteBuffer tmp = null;
        try {
            handshaking.lock();
            tmp = allocate(BufType.APPLICATION);
            while (hs_status != HandshakeStatus.FINISHED &&
                   hs_status != HandshakeStatus.NOT_HANDSHAKING) {
                WrapperResult r = null;
                switch (hs_status) {
                    case NEED_TASK:
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            /* run in current thread, because we are already
                             * running an external Executor
                             */
                            task.run();
                        }
                        /* fall thru - call wrap again */
                    case NEED_WRAP:
                        tmp.clear();
                        tmp.flip();
                        r = new WrapperResult();
                        r.result = wrapAndSend(tmp);
                        break;

                    case NEED_UNWRAP:
                        tmp.clear();
                        r = receiveAndUnwrap(tmp);
                        if (r.buf != tmp) {
                            allocator.free(tmp);
                            tmp = r.buf;
                        }
                        assert tmp.position() == 0;
                        break;
                }
                hs_status = r.result.getHandshakeStatus();
            }
        } finally {
            allocator.free(tmp);
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

    public static class WrapperResult {
        SSLEngineResult result;

        /* if passed in buffer was not big enough then the
         * a reallocated buffer is returned here
         */
        ByteBuffer buf;
    }
}
