package org.example.network.tcp.io;

import org.example.network.event.PipelineReadableListener;
import org.example.network.pipe.Pipeline;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.LockSupport;

class ConnectionTask implements Runnable, PipelineReadableListener {

    private final Pipeline pipeline;
    private final int bufCapacity;
    private final InetSocketAddress address;
    private Thread executeThread;

    ConnectionTask(Pipeline pipeline, int bufCapacity) {
        this(pipeline, bufCapacity, null);
    }
    ConnectionTask(Pipeline pipeline, int bufCapacity, InetSocketAddress address) {
        this.pipeline = pipeline;
        this.bufCapacity = bufCapacity;
        this.address = address;
    }


    @Override
    public void run() {
        try (SocketChannel channel = (SocketChannel) pipeline.getChannel();) {
            executeThread = Thread.currentThread();
            channel.configureBlocking(true);
            pipeline.listener(this);
            if (address != null) {
                pipeline.connect(address);
            }
            pipeline.connected();
            ByteBuffer buffer;
            while (read(pipeline, buffer = pipeline.allocate(bufCapacity)) != -1) {
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

    }

    private int read(Pipeline pipeline, ByteBuffer dst) throws IOException {
        while (!pipeline.readable()) {
            LockSupport.park();
        }
        SocketChannel channel = (SocketChannel) pipeline.getChannel();
        return channel.read(dst);
    }


    @Override
    public void isReadable(Pipeline pipeline) {
        Thread thread = executeThread;
        if (thread != null) {
            LockSupport.unpark(thread);
        }
    }
}
