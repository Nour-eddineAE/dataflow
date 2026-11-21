package com.dataflow.workspace.runs;

import com.dataflow.workspace.serving.Seconds;

import java.time.Instant;

final class EpochSeconds {

    private EpochSeconds() {}

    static Instant instant(double seconds) {
        return Instant.ofEpochMilli(Seconds.toMillis(seconds));
    }
}
