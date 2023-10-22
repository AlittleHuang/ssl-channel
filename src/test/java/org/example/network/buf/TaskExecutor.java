package org.example.network.buf;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TaskExecutor {

    public static void main(String[] args) throws InterruptedException {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(10, Thread.ofVirtual().factory());

        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            Object name = Thread.currentThread().threadId();
            System.out.println(name + "666");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, 0, 1, TimeUnit.SECONDS);
        Thread.sleep(8000);
        future.cancel(true);
        Thread.sleep(5000);


    }

}
