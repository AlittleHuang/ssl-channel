package org.example.network.jray;

import org.example.log.Logs;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;
import org.example.network.pipe.Pipeline;
import org.example.network.pipe.handlers.SslPipeHandler;
import org.example.network.tcp.nio.NioTcpClient;

import javax.net.ssl.SSLContext;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

public class ClientConnectionPool {

    public static final Logger logger = Logs.getLogger(ClientConnectionPool.class);
    private final BlockingQueue<KeeperClientHandler> queue;

    public ClientConnectionPool(InetSocketAddress remote, int size) {
        queue = new ArrayBlockingQueue<>(size);
        Thread.startVirtualThread(() -> {
            // noinspection InfiniteLoopStatement
            while (true) {
                try {
                    preConnect(remote);
                } catch (Exception e) {
                    logger.log(Level.INFO, "connect error", e);
                }
            }
        });
    }

    private void preConnect(InetSocketAddress remote) throws Exception {
        KeeperClientHandler handler = new KeeperClientHandler(queue);
        queue.put(handler);
        NioTcpClient.Config config = new NioTcpClient.Config();
        config.host = remote.getHostName();
        config.port = remote.getPort();
        config.handler = new PipeHandler() {
            @Override
            public void init(PipeContext ctx) {
                try {
                    ctx.addFirst(new SslPipeHandler(SSLContext.getDefault(), true));
                    ctx.addLast(handler);
                } catch (Exception e) {
                    ctx.fireError(e);
                }
                ctx.remove();
            }
        };
        NioTcpClient.open(config);
    }

    public CompletableFuture<Pipeline> get() throws InterruptedException {
        KeeperClientHandler poll = queue.take();
        return poll.connect();
    }

}
