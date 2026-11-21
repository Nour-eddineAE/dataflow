package com.dataflow.workspace.dashboards;

import java.util.List;

public record Dashboard(String dataset, List<TableBoard> tables, List<String> problems) {}
