package org.example.network.pipe.handlers;

import org.example.network.buf.ByteBufferUtil;
import org.example.network.event.NioEventLoopExecutor;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.tcp.TcpClient;
import org.example.network.tcp.TcpClient.Config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

class HttpProxyHandlerTest {

    public static void main(String[] args) throws IOException, InterruptedException {
        Config config = new Config();

        config.host = "www.baidu.com";
        config.port = 80;
        config.handler = new PipeHandler() {

            @Override
            public void init(PipeContext ctx) {
                ctx.addFirst(new HttpProxyHandler(new InetSocketAddress(1090)));
            }

            @Override
            public void onConnected(PipeContext ctx) throws IOException {
                String req = """
                        GET / HTTP/1.0\r
                        Connection: close\r
                        \r
                        """;
                ctx.fireWrite(ByteBuffer.wrap(req.getBytes()));
                ctx.fireConnected();
            }

            @Override
            public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
                System.out.println(ByteBufferUtil.readToString(buf));
                ctx.fireReceive(buf);
            }
        };


        TcpClient client = new TcpClient(config);
        Thread.sleep(5000);
    }

}