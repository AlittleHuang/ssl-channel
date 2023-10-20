package org.example.network.buf;


class BytesPoolTest {


    public static void main(String[] args) throws InterruptedException {
        BytesPool pool = new BytesPool(1024);
        pool.setExpiration(3000);
        byte[] required = pool.required();
        pool.pooled(required);
        pool.required();
        pool.cleanExpired();
        pool.pooled(required);
        pool.cleanExpired();
        Thread.sleep(3000);
        pool.cleanExpired();
    }

}