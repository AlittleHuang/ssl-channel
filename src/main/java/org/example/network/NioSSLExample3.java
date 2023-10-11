package org.example.network;


import org.example.network.buf.ByteBufferUtil;
import org.example.network.channel.SelectorService;
import org.example.network.channel.handler.SslChannelHandler;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class NioSSLExample3 {
    public static void main(String[] args) throws Exception {
        InetSocketAddress address = new InetSocketAddress("www.baidu.com", 443);
        Selector selector = Selector.open();
        SocketChannel channel = SocketChannel.open();
        SelectorService service = SelectorService.start(selector);
        channel.configureBlocking(false);

        int ops = SelectionKey.OP_WRITE | SelectionKey.OP_READ;

        ByteBuffer buf = ByteBuffer.allocate(1024);
        List<byte[]> bytesList = new ArrayList<>();
        CompletableFuture<Void> closed = new CompletableFuture<>();
        AtomicInteger totalBytes = new AtomicInteger();

        SslChannelHandler handler = SslChannelHandler.clientChannel(address, key -> {
            //noinspection resource
            if (key.isWritable() && key.channel() instanceof WritableByteChannel ch) {
                String req = """
                        GET / HTTP/1.0\r
                        Connection: close\r
                        \r
                        """;
                ByteBuffer reqBuf = ByteBuffer.wrap(req.getBytes());
                while (reqBuf.hasRemaining()) {
                    ch.write(reqBuf);
                }
                key.interestOps(key.interestOps() & (~SelectionKey.OP_WRITE));
            }
            //noinspection resource
            if (key.isReadable() && key.channel() instanceof ReadableByteChannel ch) {
                int read;
                buf.clear();
                while ((read = ch.read(buf)) > 0) {
                    totalBytes.getAndAdd(read);
                    buf.flip();
                    bytesList.add(ByteBufferUtil.readToArray(buf));
                    buf.clear();
                }
                if (read == -1) {
                    closed.complete(null);
                }
            }
        });
        service.register(handler);

        closed.join();

        ByteBuffer buffer = ByteBuffer.allocate(totalBytes.intValue());
        for (byte[] bytes : bytesList) {
            buffer.put(bytes);
        }
        System.out.println(totalBytes);
        System.out.println(new String(buffer.array(), StandardCharsets.UTF_8));
        channel.close();
        service.close();


    }

}
