package org.example.network.channel.pipe.handlers;

import org.example.network.tcp.TcpServer;
import org.example.network.tcp.TcpServer.Config;

import java.io.IOException;

class HttpProxyServerHandlerTest {

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        config.port = 1090;
        config.handler = new HttpProxyServerHandler();
        TcpServer.open(config);
    }

}