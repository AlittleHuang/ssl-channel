package org.example.network.buf;

import org.jetbrains.annotations.NotNull;

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

    @NotNull
    public static String copyAsString(ByteBuffer buf) {
        return new String(ByteBufferUtil.copyAsArray(buf));
    }

    public static byte[] readToArray(ByteBuffer buffer) {
        byte[] dst = new byte[buffer.remaining()];
        buffer.get(dst);
        return dst;
    }

    public static String readToString(ByteBuffer buffer) {
        return new String(readToArray(buffer));
    }

    public static String identity(ByteBuffer buffer) {
        return buffer.getClass().getSimpleName()
               + "[" + buffer.capacity() + "]"
               + "@" + Integer.toString(System.identityHashCode(buffer), 16);
    }

}
