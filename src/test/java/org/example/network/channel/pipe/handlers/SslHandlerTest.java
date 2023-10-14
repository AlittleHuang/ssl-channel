package org.example.network.channel.pipe.handlers;

import org.example.network.TpcClient;
import org.example.network.TpcClient.Config;
import org.example.network.buf.ByteBufferUtil;
import org.example.network.channel.EventLoopExecutor;
import org.example.network.channel.pipe.PipeContext;
import org.example.network.channel.pipe.PipeHandler;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.security.NoSuchAlgorithmException;

class SslHandlerTest {


    public static void main(String[] args) throws Exception {
        Config config = new Config();

        config.host = "www.baidu.com";
        config.port = 443;
        config.executor = EventLoopExecutor.start(Selector.open());
        config.handler = new PipeHandler() {

            @Override
            public void init(PipeContext ctx) {
                try {
                    ctx.addFirst(new SslPipeHandler(SSLContext.getDefault(), true));
                    ctx.addFirst(new LogHandler());
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
            }

            @Override
            public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
                System.out.println(ByteBufferUtil.readToString(buf));
                ctx.fireReceive(buf);
                if (buf == END_OF_STREAM) {
                    ctx.fireClose();
                }
            }
        };


        TpcClient client = new TpcClient(config);
        Thread.sleep(1000);
        // client.pipeline().executor().close();
    }

}