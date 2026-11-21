package com.dataflow.workspace.tasks;

import java.util.Arrays;
import java.util.Locale;

public enum TaskKind {
    PREVIEW,
    IMPACT,
    INFER;

    public static TaskKind of(String command) {
        return Arrays.stream(values())
                .filter(kind -> kind.command().equals(command))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown task '" + command + "'; use preview, impact or infer"));
    }

    public String command() {
        return name().toLowerCase(Locale.ROOT);
    }
}
