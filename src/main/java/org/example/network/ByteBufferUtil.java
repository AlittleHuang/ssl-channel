package org.example.network;

import java.nio.ByteBuffer;
import java.util.Base64;

public class ByteBufferUtil {

    public static String copyAsBase64(ByteBuffer buffer) {
        byte[] bytes = copyAsArray(buffer);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] copyAsArray(ByteBuffer buffer) {
        buffer.mark();
        byte[] bytes = readToArray(buffer);
        buffer.reset();
        return bytes;
    }

    public static byte[] readToArray(ByteBuffer buffer) {
        byte[] dst = new byte[buffer.remaining()];
        buffer.get(dst);
        return dst;
    }

}
