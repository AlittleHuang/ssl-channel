package org.example.network.event.pipe.handlers;

import org.example.network.buf.ByteBufferUtil;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.handlers.SslPipeHandler;
import org.example.network.tcp.TcpClient;
import org.example.network.tcp.TcpClient.Config;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

class SslHandlerTest {


    public static void main(String[] args) throws Exception {
        Config config = new Config();

        config.host = "www.baidu.com";
        config.port = 443;
        config.handler = new PipeHandler() {

            @Override
            public void init(PipeContext ctx) {
                try {
                    ctx.addBefore(new PipeHandler() {
                    });
                    ctx.addFirst(new SslPipeHandler(SSLContext.getDefault(), true));
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
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