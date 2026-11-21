package com.dataflow.engine.core.draft;

final class Failures {

    private static final int MAX_LENGTH = 600;

    private Failures() {}

    static String message(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String text = root.getMessage() == null
                ? root.getClass().getSimpleName()
                : root.getMessage().strip();
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) + "…" : text;
    }
}
