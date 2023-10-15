package org.example.jray;

import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.handlers.LoggingHandler;
import org.example.network.pipe.handlers.RelayHandler;
import org.example.network.tcp.TcpServer;
import org.example.network.tcp.TcpServer.Config;

import java.io.IOException;

public class LocalServer {

    public static void main(String[] args) throws IOException {
        Config config = new Config();
        config.port = 1090;
        config.host = "0.0.0.0";
        config.autoRead = false;
        config.handler = new PipeHandler() {
            @Override
            public void init(PipeContext ctx) {
                try {
                    ctx.replace(new RelayHandler(ctx.pipeline(), "127.0.0.1", 1091));
                } catch (IOException e) {
                    ctx.fireError(e);
                }
            }
        };

        TcpServer.open(config);
    }

}
