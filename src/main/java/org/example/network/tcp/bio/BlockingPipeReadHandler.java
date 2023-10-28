package org.example.network.tcp.bio;

import org.example.log.Logs;
import org.example.network.pipe.Pipeline;
import org.example.network.tcp.PipeReadHandler;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.LockSupport;

public class BlockingPipeReadHandler implements PipeReadHandler {

    public static final BlockingPipeReadHandler DEFAULT = new BlockingPipeReadHandler();
    private static final Logger logger = Logs.getLogger(BlockingPipeReadHandler.class);

    private BlockingPipeReadHandler() {
    }

    @Override
    public void handler(SocketChannel channel, Pipeline pipeline, SocketAddress remote) throws IOException {
        Thread.ofVirtual().name(threadId(channel)).start(() -> {
            try {
                ByteBuffer buffer;
                Thread thread = Thread.currentThread();
                pipeline.listener(__ -> LockSupport.unpark(thread));
                if (remote != null) {
                    pipeline.connect(remote);
                }
                pipeline.connected();
                while (read(channel, pipeline, buffer = pipeline.allocate()) >= 0) {
                    if (buffer.flip().hasRemaining()) {
                        pipeline.onReceive(buffer);
                    } else {
                        pipeline.free(buffer);
                    }
                }
                pipeline.free(buffer);
                pipeline.onReadeTheEnd();
            } catch (Exception e) {
                pipeline.onError(e);
            }
        });
    }

    private static int read(SocketChannel channel, Pipeline pipeline, ByteBuffer buffer) {
        while (!pipeline.readable()) {
            LockSupport.park();
        }
        try {
            return channel.read(buffer);
        } catch (IOException e) {
            logger.log(Level.DEBUG, () ->
                    "read filed: " + e.getClass().getName() + " : " + e.getLocalizedMessage());
            return -1;
        } catch (Exception e) {
            logger.log(Level.ERROR, () ->
                    "read filed: " + e.getClass().getName() + " : " + e.getLocalizedMessage());
            if (buffer.hasRemaining()) {
                return 0;
            } else {
                pipeline.free(buffer);
                throw e;
            }
        }
    }

    @NotNull
    private static String threadId(SocketChannel channel) {
        try {
            SocketAddress local = channel.getLocalAddress();
            SocketAddress remote = channel.getRemoteAddress();
            return local + " - " + remote;
        } catch (IOException e) {
            return String.valueOf(channel);
        }
    }

}
