package org.example.network.buf;

public class Bytes {

    public static final int K = 1024;

    public static final int M = K * K;

    public static final int G = M * K;

    private static final int MARK = K - 1;

    private static final int MARK_1 = ~MARK;


    public static int sizeFor(int size) {
        if ((size & MARK) != 0) {
            size &= MARK_1;
            size += K;
        }
        return size;
    }

}
