package com.dataflow.workspace.web;

final class Limits {

    static final int MAX_RUNS = 200;
    static final int MAX_SAMPLE = 1000;

    private Limits() {}

    static int clamp(int requested, int max) {
        return Math.max(1, Math.min(requested, max));
    }
}
