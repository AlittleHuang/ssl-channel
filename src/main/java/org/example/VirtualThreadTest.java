package org.example;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

public class VirtualThreadTest {
    private static final Lock lock = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {
        long start = System.currentTimeMillis();
        List<Thread> threads = IntStream.range(0, 10000)
                .mapToObj(i -> Thread.startVirtualThread(() -> {
                    Lock lock = new ReentrantLock();
                    // lock.lock();
                    synchronized (lock){
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    // finally {
                    //     lock.unlock();
                    // }
                }))
                .toList();
        for (Thread it : threads) {
            it.join();
        }
        System.out.println(System.currentTimeMillis() - start);
    }

}
