package org.example.jray;

import org.example.network.buf.ByteBufferUtil;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.tcp.TcpServer;
import org.example.network.tcp.TcpServer.Config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

public class EchoServer {

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        config.bindPort = 1443;
        config.bindHost = "0.0.0.0";
        config.handler = new PipeHandler() {
            @Override
            public void onReceive(PipeContext ctx, ByteBuffer buf) {
                System.out.println(ctx.pipeline().getChannel() + "\n" + ByteBufferUtil.readToString(buf));
            }
        };
        TcpServer.open(config);
        LockSupport.park();
    }

}
