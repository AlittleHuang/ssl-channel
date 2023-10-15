package org.example.concurrent;

import org.example.network.event.SelectionKeyHandlerFunction;

import java.nio.channels.SelectionKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SelectionKeyHandlerTask {

    private final ExecutorService executor;

    private final SelectionKeyHandlerFunction target;

    private final Lock lock = new ReentrantLock();

    private final AtomicBoolean needTask = new AtomicBoolean();

    public SelectionKeyHandlerTask(ExecutorService executor, SelectionKeyHandlerFunction target) {
        this.executor = executor;
        this.target = target;
    }

    public void wakeup(SelectionKey key) {
        if (!needTask.getAndSet(true)) {
            execute(key);
        }
    }

    private void execute(SelectionKey key) {
        if (needTask.get()) {
            executor.execute(() -> runTask(key));
        }
    }

    private void runTask(SelectionKey key) {
        if (lock.tryLock()) {
            try {
                while (needTask.getAndSet(false)) {
                    target.handler(key);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                lock.unlock();
                execute(key);
            }
        }
    }


}
