package com.dataflow.workspace.runs;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class EngineOutput {

    private static final String JVM_NOTE = "NOTE: Picked up ";
    private static final String PIPES = "__dagster_pipes_version";

    private EngineOutput() {}

    public static List<OutputLine> parse(String stdout, ObjectMapper json) {
        List<OutputLine> lines = new ArrayList<>();
        String flow = null;
        for (String line : stdout.split("\\R")) {
            if (line.isBlank() || line.startsWith(JVM_NOTE)) {
                continue;
            }
            JsonNode node = read(line, json);
            if (node == null) {
                lines.add(new OutputLine(null, null, null, flow, line));
            } else if (!node.has(PIPES)) {
                OutputLine entry = entry(node);
                flow = entry.flow();
                lines.add(entry);
            }
        }
        return lines;
    }

    private static OutputLine entry(JsonNode node) {
        String logger = node.path("logger_name").asString("");
        String message = node.path("message").asString("");
        if (node.hasNonNull("stack_trace")) {
            message = message + "\n" + node.path("stack_trace").asString();
        }
        return new OutputLine(
                node.path("@timestamp").asString(null),
                node.path("level").asString(null),
                logger.substring(logger.lastIndexOf('.') + 1),
                node.path("flow").asString(null),
                message);
    }

    private static JsonNode read(String line, ObjectMapper json) {
        if (!line.startsWith("{")) {
            return null;
        }
        try {
            JsonNode node = json.readTree(line);
            return node.isObject() ? node : null;
        } catch (JacksonException e) {
            return null;
        }
    }
}
