package com.dataflow.workspace.datasets;

import java.util.List;

public record PipelineGraph(List<GraphNode> nodes, List<Edge> edges) {

    public record Edge(String from, String to) {}
}
