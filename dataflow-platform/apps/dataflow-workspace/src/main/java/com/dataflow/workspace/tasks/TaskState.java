package com.dataflow.workspace.tasks;

import tools.jackson.databind.JsonNode;

public record TaskState(String taskId, String runId, String status, JsonNode result) {}
