package org.example.network.event;


import org.example.network.buf.ByteBufferUtil;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.tcp.TcpClient;
import org.example.network.tcp.TcpClient.Config;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SelectorServiceTest {

    public static void main(String[] args) throws IOException, InterruptedException {

        Config config = new Config();

        config.host = "www.baidu.com";
        config.port = 80;
        config.handler = new PipeHandler() {
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
        Thread.sleep(100);


    }


}