package com.dataflow.workspace.templates;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplatesTest {

    @Test
    void load_findsEveryTowerDocument() {
        Templates documents = Templates.load("graphql", "graphql");

        assertThat(documents.names())
                .containsExactly(
                        "automation",
                        "backfill",
                        "captured-logs",
                        "code-server",
                        "launch",
                        "partitions",
                        "reload",
                        "resource",
                        "run",
                        "run-fields",
                        "runs",
                        "schedule",
                        "schedules",
                        "start-schedule",
                        "start-sensor",
                        "stop-schedule",
                        "stop-sensor",
                        "terminate",
                        "version");
        assertThat(documents.names())
                .allSatisfy(name -> assertThat(documents.placeholders(name)).isEmpty());
    }

    @Test
    void render_ofThePipelineTemplateIsAValidSpec() {
        String content = Templates.load("templates", "yml").render("pipeline", Map.of("dataset", "rainfall"));

        PipelineSpec spec = new PipelineSpecLoader().parse(content, "rainfall");

        assertThat(spec.dataset()).isEqualTo("rainfall");
        assertThat(spec.serve())
                .singleElement()
                .satisfies(serve -> assertThat(serve.table()).isEqualTo("rainfall"));
    }
}
