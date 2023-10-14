package org.example.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SingleThreadTask {

    private final ExecutorService executor;

    private final Runnable target;

    private final Lock lock = new ReentrantLock();

    private final AtomicBoolean needTask = new AtomicBoolean();

    public SingleThreadTask(ExecutorService executor, Runnable target) {
        this.executor = executor;
        this.target = target;
    }

    public void wakeup() {
        if (!needTask.getAndSet(true)) {
            execute();
        }
    }

    private void execute() {
        if (needTask.get()) {
            executor.execute(this::runTask);
        }
    }

    private void runTask() {
        if (lock.tryLock()) {
            try {
                while (needTask.getAndSet(false)) {
                    target.run();
                }
            } finally {
                lock.unlock();
                execute();
            }
        }
    }


}
