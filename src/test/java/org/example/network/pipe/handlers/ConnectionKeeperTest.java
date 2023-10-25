package org.example.network.pipe.handlers;

import org.example.network.jray.KeeperClientHandler;
import org.example.network.jray.KeeperServerHandler;
import org.example.network.pipe.Pipeline;
import org.example.network.tcp.nio.NioTcpClient;
import org.example.network.tcp.nio.NioTcpServer;
import org.example.network.tcp.nio.NioTcpServer.Config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

class ConnectionKeeperTest {


    public static final int PORT = 1908;

    static class Client {
        public static void main(String[] args) throws IOException, InterruptedException {

            NioTcpClient.Config config = new NioTcpClient.Config();
            config.host = "127.0.0.1";
            config.port = PORT;
            KeeperClientHandler handler = new KeeperClientHandler(new ArrayDeque<>());
            config.handler = handler;
            NioTcpClient.open(config);
            Thread.sleep(1000);

            CompletableFuture<Pipeline> remove = handler.connect();
            remove.thenAccept(__ -> System.out.println("DONE"));

            LockSupport.park(Duration.ofSeconds(10).toNanos());
        }
    }

    static class Server {
        public static void main(String[] args) throws IOException {
            Config config = new Config();
            config.port = PORT;
            config.handler = new KeeperServerHandler();
            NioTcpServer.open(config);
            LockSupport.park();
        }
    }

    public static void main(String[] args) {
        InetSocketAddress address = new InetSocketAddress(12);
        System.out.println(address.getHostName());
    }

}