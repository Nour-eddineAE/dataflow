package com.dataflow.workspace.runs;

import java.util.List;

public record Partitions(boolean partitioned, String kind, List<Partition> partitions) {

    public static final Partitions NONE = new Partitions(false, null, List.of());

    public record Partition(String key, String status) {}
}
