package com.dataflow.workspace.serving;

import java.util.List;

public record ServingHealth(Activity activity, long memoryBytes, List<FailedRefresh> failedRefreshes) {

    public record Activity(long queries, long failed, Double averageMs, long slowestMs) {}

    public record FailedRefresh(String database, String view, String error) {}
}
