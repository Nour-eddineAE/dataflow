package com.dataflow.workspace.runs;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class EngineOutputTest {

    private static String resource(String path) throws IOException {
        try (var stream = Objects.requireNonNull(EngineOutputTest.class.getResourceAsStream(path))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parse_keepsLogEntriesAndPlainLinesInTheirFlowButNotPipesMessagesOrJvmNotes() throws IOException {
        List<OutputLine> lines = EngineOutput.parse(resource("/runs/engine-output.txt"), new ObjectMapper());

        assertThat(lines)
                .containsExactly(
                        new OutputLine(
                                "2026-10-25T02:43:56.776Z",
                                "INFO",
                                "EngineApplication",
                                null,
                                "Started EngineApplication in 1.916 seconds"),
                        new OutputLine(
                                "2026-10-25T02:44:10.827Z",
                                "INFO",
                                "PipelineRun",
                                "silver",
                                "Flow silver COMPLETED in 4351 ms: 9984 rows in, 9984 out, 0 dropped"),
                        new OutputLine(
                                "2026-10-25T02:44:11.002Z",
                                "ERROR",
                                "PipelineRun",
                                "history",
                                "Flow history FAILED\njava.lang.IllegalStateException: boom\n\tat Flow.run(Flow.java:1)"),
                        new OutputLine(null, null, null, "history", "Exception in thread \"shutdown\" plain text"));
    }
}
