package com.dataflow.workspace.runs;

import com.dataflow.workspace.config.WorkspaceProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class RunOutputs {

    private static final String LOGS_CAPTURED = "LogsCapturedEvent";

    private final Tower tower;
    private final int maxBytes;
    private final ObjectMapper json;

    public RunOutputs(Tower tower, ObjectMapper json, WorkspaceProperties properties) {
        this.maxBytes = Math.toIntExact(properties.tower().maxOutput().toBytes());
        this.tower = tower;
        this.json = json;
    }

    public RunOutput output(String runId) {
        List<OutputLine> lines = new ArrayList<>();
        boolean truncated = false;
        for (JsonNode event : tower.run(runId).path("eventConnection").path("events")) {
            if (LOGS_CAPTURED.equals(event.path("__typename").asString())) {
                String stdout =
                        tower.capturedOutput(runId, event.path("fileKey").asString(), maxBytes);
                truncated |= stdout.length() >= maxBytes;
                lines.addAll(EngineOutput.parse(stdout, json));
            }
        }
        return new RunOutput(lines, truncated);
    }
}
