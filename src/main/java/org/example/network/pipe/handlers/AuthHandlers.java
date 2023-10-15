package org.example.network.pipe.handlers;

import org.example.network.pipe.handlers.AuthClientHandler;
import org.example.network.pipe.handlers.AuthServerHandler;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

public class AuthHandlers {

    private static final byte[] key = {
            -74, -51, 105, -106, 68, -6, -104, -118, -70, -21, -68, -87, 7, 45, 64, -25,
            -127, 99, -30, 120, 78, 77, 26, 58, -128, -71, -94, 112, -25, 68, 66, -99
    };
    private static final AuthServerHandler AUTH_SERVER_HANDLER = new AuthServerHandler(key);
    private static final AuthClientHandler AUTH_CLIENT_HANDLER = new AuthClientHandler(key);


    public static AuthClientHandler client() {
        return AUTH_CLIENT_HANDLER;
    }

    public static AuthServerHandler server() {
        return AUTH_SERVER_HANDLER;
    }

    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 4);

        for (int i = 0; i < 2; i++) {
            UUID uuid = UUID.randomUUID();
            buffer.putLong(uuid.getLeastSignificantBits());
            buffer.putLong(uuid.getMostSignificantBits());
        }

        System.out.println(Arrays.toString(buffer.array()));

    }

}
