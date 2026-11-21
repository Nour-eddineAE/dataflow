package com.dataflow.workspace.dashboards;

import java.util.List;

public record TableBoard(
        String table,
        String label,
        String sqlName,
        long rows,
        String time,
        String latest,
        List<String> filters,
        List<Chart> charts) {}
