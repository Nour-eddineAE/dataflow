package com.dataflow.workspace.catalog;

import java.util.List;

public record TableProfile(long rows, List<ColumnProfile> columns, long elapsedMs, long bytesRead) {}
