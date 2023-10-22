package org.example.network.buf;

import org.example.log.Logs;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

public class ExpiredCacheCleaner {

    private static final Logger logger = Logs.getLogger(ExpiredCacheCleaner.class);


    protected static final ExpiredCacheCleaner INSTANCE = new ExpiredCacheCleaner();


    private final Set<Clearable> tasks = new HashSet<>();
    private Thread thread;
    private Duration period = Duration.ofSeconds(1);

    public void setPeriod(Duration period) {
        this.period = period;
    }


    public static ExpiredCacheCleaner getInstance() {
        return INSTANCE;
    }

    public void register(Clearable task) {
        synchronized (tasks) {
            tasks.add(Objects.requireNonNull(task));
            if (thread == null) {
                logger.log(Level.ALL, "start work");
                thread = Thread.startVirtualThread(this::work);
            }
        }
    }

    private void work() {
        while (!tasks.isEmpty()) {
            LockSupport.parkNanos(period.toNanos());
            synchronized (tasks) {
                tasks.removeIf(Clearable::clear);
                if (tasks.isEmpty()) {
                    logger.log(Level.DEBUG, "cleaned");
                    thread = null;
                }
            }
        }
    }

    public interface Clearable {
        boolean clear();
    }

}
