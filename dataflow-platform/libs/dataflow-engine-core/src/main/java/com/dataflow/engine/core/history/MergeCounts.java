package com.dataflow.engine.core.history;

public record MergeCounts(long newFacts, long changedFacts, long staleVersions) {

    public static final MergeCounts NONE = new MergeCounts(0, 0, 0);

    public MergeCounts plus(MergeCounts other) {
        return new MergeCounts(
                newFacts + other.newFacts, changedFacts + other.changedFacts, staleVersions + other.staleVersions);
    }
}
