package com.dataflow.workspace.alerts;

public record Alert(String name, String dataset, String severity, String state, String since, String summary) {}
