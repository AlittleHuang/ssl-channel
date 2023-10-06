package org.example.network;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;

public abstract class SSLProvider2 {
    private final SSLEngine engine;
    private final Pipe out, in;
    private final ByteBuffer outBuf, inBuf;
    private final ByteBuffer serverWrap, serverUnwrap;

    private final Pipe decryptedInput;

    public SSLProvider2(SSLEngine engine, int capacity) {
        this.out = getPipe();
        this.in = getPipe();
        this.decryptedInput = getPipe();
        this.outBuf = ByteBuffer.allocate(capacity);
        this.inBuf = ByteBuffer.allocate(capacity);
        this.serverWrap = ByteBuffer.allocate(capacity);
        this.serverUnwrap = ByteBuffer.allocate(capacity);
        this.engine = engine;
        // Thread.startVirtualThread(this);
    }

    private static Pipe getPipe() {
        try {
            Pipe open = Pipe.open();
            open.source().configureBlocking(false);
            return open;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void onInput(ByteBuffer decrypted) {

        while (decrypted.hasRemaining()) {
            try {
                // noinspection resource
                decryptedInput.sink().write(decrypted);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public int read(ByteBuffer decrypted) {
        try {
            // noinspection resource
            return decryptedInput.source().read(decrypted);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public abstract void onOutput(ByteBuffer encrypted);

    public abstract void onFailure(Exception ex);

    public abstract void onSuccess();

    public abstract void onClosed();

    public void write(final ByteBuffer data) throws IOException {
        write(out, data);
        handshake();
    }

    private static void write(Pipe pipe, ByteBuffer data) throws IOException {
        while (data.hasRemaining()) {
            // noinspection resource
            pipe.sink().write(data);
        }
    }

    public void notify(final ByteBuffer data) throws IOException {
        write(in, data);
        // Thread.startVirtualThread(this);
        handshake();
    }

    public void handshake() throws IOException {
        // noinspection StatementWithEmptyBody
        while (this.isHandShaking()) ;
    }

    private synchronized boolean isHandShaking() throws IOException {
        switch (engine.getHandshakeStatus()) {
            case NOT_HANDSHAKING: {
                boolean occupied = false;
                occupied |= updated(this.wrap());
                occupied |= this.unwrap();
                return occupied;
            }
            case NEED_WRAP:
                return updated(this.wrap());
            case NEED_UNWRAP:
                return this.unwrap();
            case NEED_TASK:
                Runnable sslTask = engine.getDelegatedTask();
                if (sslTask != null) {
                    sslTask.run();
                }
                handshake();
                return false;
            case FINISHED:
                throw new IllegalStateException("FINISHED");
        }

        return true;
    }


    private SSLEngineResult wrap() throws IOException {
        SSLEngineResult wrapResult;

        // noinspection resource
        out.source().read(outBuf);
        outBuf.flip();
        int old = outBuf.remaining();
        wrapResult = engine.wrap(outBuf, serverWrap);
        if (old != 0 || outBuf.position() != 0) {
            System.out.println("outBuf.remaining:" + old + "->" + outBuf.remaining());
        }
        outBuf.compact();


        switch (wrapResult.getStatus()) {
            case OK:
                if (serverWrap.position() > 0) {
                    serverWrap.flip();
                    this.onOutput(serverWrap);
                    serverWrap.compact();
                }

                break;
            case BUFFER_UNDERFLOW:
                break;
            case BUFFER_OVERFLOW:
                throw new IllegalStateException("failed to wrap");
            case CLOSED:
                throw new IllegalStateException("closed");
        }

        return wrapResult;
    }

    private static boolean updated(SSLEngineResult wrapResult) {
        return wrapResult.bytesConsumed() != 0 || wrapResult.bytesProduced() != 0;
    }

    private boolean unwrap() {
        SSLEngineResult unwrapResult;

        try {
            // noinspection resource
            in.source().read(inBuf);
            inBuf.flip();
            unwrapResult = engine.unwrap(inBuf, serverUnwrap);
            inBuf.compact();
        } catch (Exception ex) {
            this.onFailure(ex);
            return false;
        }

        if (serverUnwrap.position() > 0) {
            serverUnwrap.flip();
            System.out.println(serverUnwrap.remaining());
            this.onInput(serverUnwrap);
            serverUnwrap.compact();
        }
        switch (unwrapResult.getStatus()) {
            case OK:
                break;

            case CLOSED:
                this.onClosed();
                return false;

            case BUFFER_OVERFLOW:
                throw new IllegalStateException("failed to unwrap");

            case BUFFER_UNDERFLOW:
                return false;
        }

        if (unwrapResult.getHandshakeStatus() == HandshakeStatus.FINISHED) {
            this.onSuccess();
            return false;
        }

        return true;
    }
}
