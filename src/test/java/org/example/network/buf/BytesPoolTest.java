package org.example.network.buf;


class BytesPoolTest {


    public static void main(String[] args) throws InterruptedException {
        BytesPool pool = new BytesPool(1024);
        pool.setExpiration(2000);
        byte[] a = pool.required();
        byte[] b = pool.required();
        pool.pooled(a);
        pool.pooled(b);
        Thread.sleep(3000);
        pool.pooled(a);
        pool.pooled(b);
        Thread.sleep(3000);

    }

}