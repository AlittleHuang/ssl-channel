package org.example.network.pipe;

import org.example.log.Logs;
import org.example.network.buf.ByteBufferAllocator;
import org.example.network.buf.ByteBufferUtil;
import org.example.network.buf.Bytes;
import org.example.network.buf.PooledAllocator;
import org.example.network.pipe.handlers.HttpProxyServerHandler;
import org.example.network.pipe.handlers.HttpProxyServerHandler.ProxyRequest;

import java.lang.System.Logger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;

import static java.lang.System.Logger.Level.TRACE;


public class HttpProxyHeaderReader {

    private static final Logger logger = Logs.getLogger(HttpProxyServerHandler.class);

    private static final byte[] DOUBLE_NEWLINE = "\r\n\r\n".getBytes();


    private final ByteBufferAllocator allocator;
    private final int initialCapacity;
    private ByteBuffer request;

    public HttpProxyHeaderReader() {
        this(PooledAllocator.GLOBAL, Bytes.DEF_CAP);
    }

    public HttpProxyHeaderReader(Pipeline pipeline) {
        this.allocator = pipeline;
        this.initialCapacity = pipeline.getBufCap();
    }

    public HttpProxyHeaderReader(ByteBufferAllocator allocator, int initialCapacity) {
        this.allocator = allocator;
        this.initialCapacity = initialCapacity;
    }

    public boolean update(ByteBuffer buf) {
        if (request == null) {
            request = allocator.allocate(initialCapacity);
        }
        request.put(buf);
        return endRequest();
    }

    public ProxyRequest getRequest() {
        logger.log(TRACE, () -> "request header: " + ByteBufferUtil.copyAsString(request));
        String line = getFirstLine();
        String[] split = line.split(" ");
        if (split.length != 3) {
            throw new IllegalStateException("Request format error");
        }

        String method = split[0].toUpperCase();
        String url = split[1];
        String version = split[2];
        String host;
        int port;
        ByteBuffer forward = null;

        if ("CONNECT".equals(method)) {
            split = url.split(":");
            if (split.length != 2) {
                throw new IllegalStateException("Request format error");
            }
            host = split[0];
            port = Integer.parseInt(split[1]);
        } else if (List.of("GET", "HEAD", "OPTIONS").contains(method)) {
            URI uri = URI.create(url);
            host = uri.getHost();
            port = uri.getPort();
            if (port <= 0) {
                String scheme = uri.getScheme();
                if ("https".equals(scheme)) {
                    port = 443;
                } else if ("http".equals(scheme)) {
                    port = 80;
                } else {
                    throw new IllegalStateException("scheme " + scheme + " error");
                }
            }
            forward = request.flip();
        } else {
            throw new IllegalStateException("method " + method + " not support");
        }
        return new ProxyRequest(version, host, port, method, forward);

    }


    private boolean endRequest() {
        byte[] end = DOUBLE_NEWLINE;
        int rl = request.position();
        int el = end.length;
        if (rl <= el) {
            return false;
        }
        for (int i = el - 1, j = rl - 1; i >= 0; i--, j--) {
            if (end[i] != request.get(j)) {
                return false;
            }
        }
        return true;
    }

    public String getFirstLine() {
        ByteBuffer buf = this.request;
        int length = 0;
        for (int i = 1; i < buf.position(); i++) {
            if (buf.get(i - 1) == '\r' && buf.get(i) == '\n') {
                length = i - 1;
                break;
            }
        }
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) buf.get(i));
        }
        return builder.toString();
    }

}
