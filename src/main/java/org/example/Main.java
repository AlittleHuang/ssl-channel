package org.example;

import org.example.network.NetworkMappingServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {

    static Selector selector;

    public static void main(String[] args) throws IOException {
        InetSocketAddress bind = new InetSocketAddress(8001);
        InetSocketAddress remote = new InetSocketAddress("10.0.0.210", 8001);
        NetworkMappingServer server = new NetworkMappingServer(bind, remote);
        server.closeFuture().join();
    }


}