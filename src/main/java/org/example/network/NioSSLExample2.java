package org.example.network;

import org.example.network.SslPipe.WrapperResult;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NioSSLExample2 {
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
        SslPipe sslPipe = new SslPipe(CachedBufferAllocator.heap(), channel, engine);
        String req = """
                GET / HTTP/1.0\r
                Connection: close\r
                \r
                """;
        ByteBuffer decrypted = ByteBuffer.allocate(1024);
        sslPipe.wrapAndSend(ByteBuffer.wrap(req.getBytes()));
        // NIO selector
        while (true) {
            key.selector().select();
            Iterator<SelectionKey> keys = key.selector().selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey k = keys.next();
                keys.remove();
                if (k.isReadable()) {
                    decrypted.clear();
                    WrapperResult result = sslPipe.receiveData(decrypted);
                    if (result.buf != decrypted) {
                        decrypted = result.buf;
                    }
                    decrypted.flip();
                    print(decrypted);
                }
            }
        }
    }

    private static void print(ByteBuffer decrypted) {
        byte[] dst = new byte[decrypted.remaining()];
        decrypted.get(dst);
        String response = new String(dst);
        System.out.print(response);
        System.out.flush();
    }
}
