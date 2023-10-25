package org.example.network.tcp.io;

import org.example.network.pipe.PipeHandler;

public class TcpClient {


    private TcpClient(Config config) {

    }

    public TcpClient open(Config config) {
        return new TcpClient(config);
    }

    public static class Config {

        public String host;

        public int port;

        public PipeHandler handler;

        public int bufCapacity;


        public boolean autoRead = true;
    }

}
