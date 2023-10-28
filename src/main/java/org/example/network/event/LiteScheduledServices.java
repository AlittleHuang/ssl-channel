package org.example.network.event;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LiteScheduledServices {

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    public static ScheduledExecutorService getExecutor() {
        return EXECUTOR;
    }
}
