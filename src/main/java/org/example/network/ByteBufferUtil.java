package org.example.network;

import java.nio.ByteBuffer;
import java.util.Base64;

public class ByteBufferUtil {

    public static String toBase64(ByteBuffer buffer) {

        int remaining = buffer.remaining();
        buffer.mark();
        byte[] bytes = new byte[remaining];
        buffer.get(bytes);
        buffer.reset();
        return Base64.getEncoder().encodeToString(bytes);
    }

}
