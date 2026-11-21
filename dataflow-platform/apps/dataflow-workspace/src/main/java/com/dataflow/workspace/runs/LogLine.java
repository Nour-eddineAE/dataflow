package com.dataflow.workspace.runs;

public record LogLine(String timestamp, String level, String type, String message) {}
