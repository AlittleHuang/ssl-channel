package org.example.network.channel;


import org.example.network.TpcClient;
import org.example.network.TpcClient.Config;
import org.example.network.buf.ByteBufferUtil;
import org.example.network.channel.pipe.PipeHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SelectorServiceTest {

    public static void main(String[] args) throws IOException {

        Config config = Config.builder()
                .host("www.baidu.com")
                .port(80)
                .handler(PipeHandler.initHandler(ctx -> {
                    ctx.addLast(PipeHandler.connectedHandler(c -> {
                        String req = """
                                GET / HTTP/1.0\r
                                Connection: close\r
                                \r
                                """;
                        c.fireWrite(ByteBuffer.wrap(req.getBytes()));
                        c.remove();
                    }));

                    ctx.addLast(PipeHandler.readHandler((context, buf) -> {
                        System.out.println(ByteBufferUtil.readToString(buf));
                        context.fireReceive(buf);
                    }));

                    ctx.remove();
                }))
                .build();

        TpcClient client = new TpcClient(config);

    }


}