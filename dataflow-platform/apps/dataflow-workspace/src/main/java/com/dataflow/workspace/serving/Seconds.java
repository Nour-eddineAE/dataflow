package com.dataflow.workspace.serving;

import java.time.Duration;

public final class Seconds {

    private static final long MILLIS = Duration.ofSeconds(1).toMillis();

    private Seconds() {}

    public static long toMillis(double seconds) {
        return Math.round(seconds * MILLIS);
    }
}
