package org.example.network.event.pipe.handlers;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.handlers.HttpProxyServerHandler;
import org.example.network.tcp.TcpServer;
import org.example.network.tcp.TcpServer.Config;

import java.io.IOException;

class HttpProxyServerHandlerTest {

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        config.bindPort = 1090;
        config.handler = new PipeHandler() {
            @Override
            public void init(PipeContext ctx) {
                ctx.replace(new HttpProxyServerHandler());
            }
        };
        TcpServer.open(config);
    }

}