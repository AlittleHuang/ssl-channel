package org.example.network.jray;

import java.net.InetSocketAddress;

class ClientConnectionPoolTest {

    public static void main(String[] args) throws InterruptedException {
        InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 1908);
        ClientConnectionPool pool = new ClientConnectionPool(remote, 10);
        Thread.sleep(1000);

    }

}