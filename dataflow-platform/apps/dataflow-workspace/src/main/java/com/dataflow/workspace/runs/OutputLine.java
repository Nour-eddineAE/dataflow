package com.dataflow.workspace.runs;

public record OutputLine(String timestamp, String level, String logger, String flow, String message) {}
