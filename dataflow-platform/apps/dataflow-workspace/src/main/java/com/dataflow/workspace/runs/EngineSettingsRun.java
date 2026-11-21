package com.dataflow.workspace.runs;

public record EngineSettingsRun(String dataset, String runId, String at, String settings) {

    EngineSettingsRun(String dataset, Recorded recorded) {
        this(dataset, recorded.runId(), recorded.at(), recorded.settings());
    }

    public record Recorded(String runId, String at, String settings) {}
}
