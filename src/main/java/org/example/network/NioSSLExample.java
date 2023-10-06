package org.example.network;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NioSSLExample {
    public static void main(String[] args) throws Exception {
        InetSocketAddress address = new InetSocketAddress("www.baidu.com", 443);
        Selector selector = Selector.open();
        SocketChannel channel = SocketChannel.open();
        channel.connect(address);
        channel.configureBlocking(false);
        int ops = SelectionKey.OP_CONNECT | SelectionKey.OP_READ;

        SelectionKey key = channel.register(selector, ops);

        // create the worker threads
        final Executor ioWorker = Executors.newSingleThreadExecutor();
        final Executor taskWorkers = Executors.newFixedThreadPool(2);

        // create the SSLEngine
        final SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(true);
        engine.beginHandshake();
        final int ioBufferSize = 32 * 1024;
        final NioSSLProvider ssl = new NioSSLProvider(key, engine, ioBufferSize, ioWorker, taskWorkers) {
            @Override
            public void onFailure(Exception ex) {
                System.out.println("handshake failure");
                ex.printStackTrace();
            }

            @Override
            public void onSuccess() {
                System.out.println("handshake success");
                SSLSession session = engine.getSession();
                try {
                    System.out.println("local principal: " + session.getLocalPrincipal());
                    System.out.println("remote principal: " + session.getPeerPrincipal());
                    System.out.println("cipher: " + session.getCipherSuite());
                    System.out.println("---\n\n");
                } catch (Exception exc) {
                    exc.printStackTrace();
                }

                // HTTP request
                String http = """
                        GET / HTTP/1.0\r
                        Connection: close\r
                        \r
                        """;
                byte[] data = http.getBytes();
                ByteBuffer send = ByteBuffer.wrap(data);
                try {
                    this.write(send);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }


            @Override
            public void onClosed() {
                ByteBuffer decrypted = ByteBuffer.allocate(1024);
                List<byte[]> bytes = new ArrayList<>();
                int size = 0;
                while (read(decrypted) > 0) {
                    decrypted.flip();
                    if (decrypted.hasRemaining()) {
                        byte[] array = toArray(decrypted);
                        bytes.add(array);
                        size += array.length;
                    }
                    decrypted.clear();
                }
                ByteBuffer buffer = ByteBuffer.allocate(size);
                for (byte[] bs : bytes) {
                    buffer.put(bs);
                }
                System.out.println(new String(buffer.array()));
            }
        };

        // NIO selector
        while (true) {
            key.selector().select();
            Iterator<SelectionKey> keys = key.selector().selectedKeys().iterator();
            while (keys.hasNext()) {
                keys.next();
                keys.remove();
                ssl.processInput();
            }
        }
    }

    private static void print(ByteBuffer decrypted) {
        byte[] dst = toArray(decrypted);
        String response = new String(dst);
        System.out.print(response);
        System.out.flush();
    }

    private static byte[] toArray(ByteBuffer decrypted) {
        byte[] dst = new byte[decrypted.remaining()];
        decrypted.get(dst);
        return dst;
    }



}
