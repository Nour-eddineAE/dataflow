package com.dataflow.engine.cli;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum TaskKind {
    PREVIEW,
    IMPACT,
    INFER,
    CLEAN;

    public static Optional<TaskKind> of(String command) {
        return Arrays.stream(values())
                .filter(kind -> kind.command().equals(command))
                .findFirst();
    }

    public String command() {
        return name().toLowerCase(Locale.ROOT);
    }
}
