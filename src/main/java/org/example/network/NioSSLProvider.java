package org.example.network;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Executor;

public abstract class NioSSLProvider extends SSLProvider2 {
    private final ByteBuffer buffer = ByteBuffer.allocate(32 * 1024);
    private final SelectionKey key;

    public NioSSLProvider(SelectionKey key, SSLEngine engine, int bufferSize, Executor ioWorker, Executor taskWorkers) throws IOException {
        super(engine, bufferSize);
        this.key = key;
        handshake();
    }

    @Override
    public void onOutput(ByteBuffer encrypted) {
        try {
            ((WritableByteChannel) this.key.channel()).write(encrypted);
        } catch (IOException exc) {
            throw new IllegalStateException(exc);
        }
    }

    public boolean processInput() throws IOException {
        buffer.clear();
        int bytes;
        try {
            // noinspection resource
            bytes = ((ReadableByteChannel) this.key.channel()).read(buffer);

        } catch (IOException ex) {
            bytes = -1;
        }
        if (bytes == -1) {
            return false;
        }
        buffer.flip();
        this.notify(buffer);
        return true;
    }
}