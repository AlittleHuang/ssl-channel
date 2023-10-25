package org.example.network.tcp;

import org.example.network.buf.ByteBufferUtil;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.tcp.nio.NioTcpServer;
import org.example.network.tcp.nio.NioTcpServer.Config;

import java.io.IOException;
import java.nio.ByteBuffer;

class TcpServerTest {

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        config.port = 10010;
        config.handler = new PipeHandler() {
            @Override
            public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
                System.out.println(ByteBufferUtil.readToString(buf));
            }
        };
        NioTcpServer.open(config);
    }

}