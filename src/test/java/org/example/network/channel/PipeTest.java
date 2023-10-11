package org.example.network.channel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.Pipe.SourceChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class PipeTest {

    public static void main(String[] args) throws IOException {
        Pipe open = Pipe.open();
        // open.sink().write(ByteBuffer.wrap(new byte[]{0}));

        SourceChannel source = open.source();
        source.configureBlocking(false);

        Selector selector = Selector.open();
        source.register(selector, SelectionKey.OP_READ);
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(1000);
                source.close();
                selector.wakeup();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        selector.select();
        SelectionKey key = selector.selectedKeys().iterator().next();
        System.out.println(source.read(ByteBuffer.allocate(1)));

    }

}
