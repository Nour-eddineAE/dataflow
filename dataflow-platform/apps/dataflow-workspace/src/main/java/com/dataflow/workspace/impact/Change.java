package com.dataflow.workspace.impact;

import java.util.List;

public record Change(Severity severity, Area area, String title, String detail, List<String> tables) {}
