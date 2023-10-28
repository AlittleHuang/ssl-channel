package org.example.network.tcp;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.tcp.TcpClient;
import org.example.network.tcp.TcpClient.Config;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

class TcpClientTest {


    public static void main(String[] args) throws Exception {
        Config config = new Config();
        config.host = "127.0.0.1";
        config.port = 1443;
        config.handler = new PipeHandler() {

            @Override
            public void onConnected(PipeContext ctx) throws IOException {
                ctx.fireWrite(ByteBuffer.wrap("hello".getBytes()));
            }
        };

        TcpClient client = TcpClient.open(config);
        Thread.sleep(1000);

    }

}