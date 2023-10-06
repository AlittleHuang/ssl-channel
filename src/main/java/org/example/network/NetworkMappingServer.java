package org.example.network;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NetworkMappingServer {

    private static final Logger LOG = Logger.getLogger(NetworkMappingServer.class.getName());

    private final ByteBuffer buffer = ByteBuffer.allocate(1024 * 8);

    private final Selector selector;

    private final ServerSocketChannel server;

    private final InetSocketAddress remote;

    private final CompletableFuture<Void> close = new CompletableFuture<>();

    public NetworkMappingServer(InetSocketAddress bind, InetSocketAddress remote) throws IOException {
        this.selector = Selector.open();
        this.server = ServerSocketChannel.open();
        this.remote = remote;

        server.configureBlocking(false);
        server.bind(bind);
        server.register(selector, SelectionKey.OP_ACCEPT);

        Thread.startVirtualThread(this::handlerSelector);
    }

    private void handlerSelector() {
        while (server.isOpen()) {
            try {
                selector.select();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "select error", e);
            }
            if (!selector.isOpen()) {
                break;
            }
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isAcceptable()) {
                    try {
                        onAccept(key);
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, e, () -> "accept " + key + " error");
                    }
                }
                if (key.isReadable()) {
                    try {
                        onRead(key);
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, e, () -> "read " + key + " error");
                    }
                }
            }
        }
        close.complete(null);
    }

    private void onAccept(SelectionKey key) throws IOException {
        ServerSocketChannel channel = (ServerSocketChannel) key.channel();
        SocketChannel localChannel = channel.accept();
        LOG.info("accept " + localChannel);
        localChannel.configureBlocking(false);
        SocketChannel remoteChannel = SocketChannel.open();
        remoteChannel.configureBlocking(false);
        SelectionKey remoteKey = remoteChannel.register(selector, SelectionKey.OP_READ);
        remoteKey.attach(localChannel);
        SelectionKey localKey = localChannel.register(selector, SelectionKey.OP_READ);
        localKey.attach(remoteChannel);
        try {
            remoteChannel.connect(this.remote);
        } catch (IOException e) {
            localChannel.close();
            throw e;
        }
    }

    private void onRead(SelectionKey key) throws IOException {
        SocketChannel read = (SocketChannel) key.channel();
        SocketChannel write = (SocketChannel) key.attachment();
        if (write.isConnectionPending()) {
            write.finishConnect();
            LOG.info(() -> "connected " + write);
        }
        if (!write.isConnected()) {
            return;
        }
        int len;
        while ((len = read.read(buffer)) > 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                write.write(buffer);
            }
            buffer.clear();
        }
        if (len < 0) {
            try (read; write) {
                LOG.info(() -> "close " + write.socket());
                LOG.info(() -> "close " + read.socket());
            }
        }
    }

    public void close() {
        close(selector);
        close(server);
    }

    private static void close(Closeable closeable) {
        try (closeable) {
            LOG.info(() -> "close " + closeable);
        } catch (IOException e) {
            LOG.log(Level.WARNING, e, () -> "close " + closeable + " error");
        }
    }

    public SocketAddress getBind() throws IOException {
        return server.getLocalAddress();
    }

    public CompletableFuture<Void> closeFuture() {
        return close;
    }

}
