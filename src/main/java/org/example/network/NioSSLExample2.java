package org.example.network;


import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

        // create the SSLEngine
        final SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(true);
        SslClientChannel sslPipe = new SslClientChannel(CachedBufferAllocator.heap(), channel, engine);
        String req = """
                GET / HTTP/1.0\r
                Connection: close\r
                \r
                """;
        ByteBuffer buf = ByteBuffer.allocate(1024);
        sslPipe.write(ByteBuffer.wrap(req.getBytes()));
        List<byte[]> bytesList = new ArrayList<>();
        int totalBytes = 0;
        // NIO selector
        while (true) {
            // noinspection resource
            key.selector().select();
            // noinspection resource
            Iterator<SelectionKey> keys = key.selector().selectedKeys().iterator();
            int read = 0;
            while (keys.hasNext()) {
                keys.next();
                keys.remove();
                buf.clear();
                while ((read = sslPipe.read(buf)) > 0) {
                    totalBytes += read;
                    buf.flip();
                    bytesList.add(readAll(buf));
                    buf.clear();
                }
            }
            if (read == -1) {
                break;
            }
        }
        ByteBuffer buffer = ByteBuffer.allocate(totalBytes);
        for (byte[] bytes : bytesList) {
            buffer.put(bytes);
        }
        System.out.println(totalBytes);
        System.out.println(sslPipe.getTotalResult());
        System.out.println(new String(buffer.array(), StandardCharsets.UTF_8));
        channel.close();

    }

    private static byte[] readAll(ByteBuffer buffer) {
        byte[] dst = new byte[buffer.remaining()];
        buffer.get(dst);
        return dst;
    }
}
