package org.example.network;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

public abstract class NioSSLProvider extends SSLProvider2 {
    private final ByteBuffer buffer = ByteBuffer.allocate(32 * 1024);
    private final SelectionKey key;

    public NioSSLProvider(SelectionKey key, SSLEngine engine, int bufferSize) throws IOException {
        super(engine, bufferSize);
        this.key = key;
        handshake();
    }

    @Override
    public void onOutput(ByteBuffer encrypted) {
        try {
            //noinspection resource
            int write = ((WritableByteChannel) this.key.channel()).write(encrypted);
            System.out.println("--- write:" + write);
        } catch (IOException exc) {
            throw new IllegalStateException(exc);
        }
    }

    public void processInput() throws IOException {
        buffer.clear();
        int bytes;
        try {
            // noinspection resource
            bytes = ((ReadableByteChannel) this.key.channel()).read(buffer);
            if (bytes > 0) {
                System.out.println("--- read:" + bytes);
            }
        } catch (IOException ex) {
            bytes = -1;
        }
        if (bytes == -1) {
            return;
        }
        buffer.flip();
        this.notify(buffer);
    }
}