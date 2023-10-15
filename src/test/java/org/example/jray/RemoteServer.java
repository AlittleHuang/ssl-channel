package org.example.jray;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.handlers.AuthHandlers;
import org.example.network.pipe.handlers.HttpProxyServerInitializer;
import org.example.network.tcp.TcpServer;
import org.example.network.tcp.TcpServer.Config;

import java.io.IOException;

public class RemoteServer {

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        config.port = 1091;
        config.handler = new PipeHandler() {
            @Override
            public void init(PipeContext ctx) {
                ctx.addBefore(AuthHandlers.server());
                ctx.addBefore(new HttpProxyServerInitializer());
            }
        };
        TcpServer.open(config);
    }

}
