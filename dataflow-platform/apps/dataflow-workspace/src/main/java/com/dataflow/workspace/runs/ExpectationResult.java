package com.dataflow.workspace.runs;

public record ExpectationResult(String name, String check, String action, Long evaluated, Long failing) {}
