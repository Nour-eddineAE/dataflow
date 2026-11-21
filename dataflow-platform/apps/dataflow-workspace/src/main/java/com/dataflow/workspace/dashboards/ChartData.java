package com.dataflow.workspace.dashboards;

import java.util.List;

public record ChartData(
        String chart,
        String sql,
        String grain,
        List<List<Object>> rows,
        boolean truncated,
        long elapsedMs,
        boolean cached) {

    ChartData fromCache() {
        return new ChartData(chart, sql, grain, rows, truncated, elapsedMs, true);
    }
}
