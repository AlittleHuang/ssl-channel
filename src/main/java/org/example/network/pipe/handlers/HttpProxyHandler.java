package org.example.network.pipe.handlers;

import org.example.network.buf.Bytes;
import org.example.network.pipe.PipeContext;
import org.example.network.pipe.PipeHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Objects;

public class HttpProxyHandler implements PipeHandler {

    private final InetSocketAddress server;
    private InetSocketAddress target;
    private boolean established;
    private ByteBuffer request;

    public HttpProxyHandler(InetSocketAddress server) {
        this.server = Objects.requireNonNull(server);
    }

    @Override
    public void onConnect(PipeContext ctx, InetSocketAddress address) throws IOException {
        ctx.fireConnect(server);
        this.target = address;
    }

    @Override
    public void onConnected(PipeContext ctx) throws IOException {
        String reqTemplate = """
                CONNECT {0}:{1} HTTP/1.1\r
                Host: {0}:{1}\r
                \r
                """;
        String reqMsg = MessageFormat.format(
                reqTemplate,
                target.getHostName(),
                Integer.toString(target.getPort())
        );
        ByteBuffer req = ByteBuffer.wrap(reqMsg.getBytes());
        ctx.fireWrite(req);
    }

    @Override
    public void onReceive(PipeContext ctx, ByteBuffer buf) throws IOException {
        if (established) {
            ctx.fireReceive(buf);
        } else {
            establishedConnection(ctx, buf);
        }
    }

    private void establishedConnection(PipeContext ctx, ByteBuffer buf) throws IOException {
        putBuf(ctx, buf);
        int len = HttpUtil.DOUBLE_NEWLINE.length;
        if (request.position() > len) {
            byte[] end = new byte[4];
            request.get(request.position() - len, end);
            if (Arrays.equals(end, HttpUtil.DOUBLE_NEWLINE)) {
                established = true;
                if (isOk()) {
                    ctx.fireConnected();
                } else {
                    ctx.fireClose();
                }
                ctx.free(request);
                ctx.remove();
                request = null;
            }
        }
    }

    private boolean isOk() {
        byte[] dst = new byte[16];
        request.rewind().get(dst);
        return new String(dst).contains("200");
    }

    private void putBuf(PipeContext ctx, ByteBuffer buf) {
        if (request == null) {
            request = ctx.allocate(Bytes.DEF_CAP);
        }
        if (request.remaining() < buf.remaining()) {
            int capacity = request.capacity();
            int min = request.position() + buf.remaining();
            while (capacity < min) {
                capacity <<= 1;
            }
            ByteBuffer newBuf = ctx.allocate(capacity);
            newBuf.put(request.flip());
            ctx.free(request);
            request = newBuf;
        }
        request.put(buf);
    }
}
