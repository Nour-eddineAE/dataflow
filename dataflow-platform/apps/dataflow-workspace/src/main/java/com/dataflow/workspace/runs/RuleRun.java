package com.dataflow.workspace.runs;

public record RuleRun(String rule, String runId, String at, long failing, long evaluated) {}
