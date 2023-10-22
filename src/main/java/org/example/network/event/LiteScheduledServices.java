package org.example.network.event;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

public class LiteScheduledServices {

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    public static ScheduledExecutorService getExecutor() {
        return EXECUTOR;
    }
}
