package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.SparkTest;
import com.dataflow.engine.core.flows.Sources;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.run.HttpTimeouts;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.lake.Lake;
import java.time.Duration;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SparkTest
class DraftTest {

    private static final String RUN = "task-1";
    private final SparkSession spark;

    @TempDir
    Path dir;

    private String template;
    private DraftSample samples;
    private Lake lake;

    DraftTest(SparkSession spark) {
        this.spark = spark;
    }

    private static PipelineSpec spec(String text) {
        return new PipelineSpecLoader().parse(text, "readings");
    }

    @BeforeEach
    void setUp() throws IOException {
        Path source = Files.createDirectories(dir.resolve("source"));
        Files.writeString(source.resolve("readings_1.csv"), """
            sensor_id,read_at,temp_c,revised_at
            s1,2026-10-01 10:00:00,21.5,2026-10-01 10:05:00
            s1,2026-10-01 11:00:00,22.5,2026-10-01 11:05:00
            s2,2026-10-01 10:00:00,,2026-10-01 10:05:00
            s3,2026-10-01 10:00:00,99.0,2026-10-01 10:05:00
            """);
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/plans/readings/pipeline.yml"))) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__LANDING_SOURCE__", source.toString());
        }
        lake = new Lake(dir.resolve("lake").toUri().toString());
        samples = new DraftSample(spark, lake, new DeltaTables(spark), new Sources(dir.resolve("cache"), new HttpTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(30))));
    }

    @Test
    void preview_ofANewDataset_readsTheSourceAndChecksEveryRule() {
        PipelineSpec draft = spec(template);

        Preview preview;
        try (Sample sample = samples.take(draft, 100, RUN)) {
            preview = new DraftPreview(spark, lake, new DeltaTables(spark), Steps.NONE).preview(draft, sample, RUN);
        }

        assertThat(preview.origin()).isEqualTo(Sample.Origin.SOURCE);
        assertThat(preview.rows()).isEqualTo(4);
        assertThat(preview.rawColumns()).extracting(Tabular.Field::name).startsWith("sensor_id", "read_at", "temp_c");
        assertThat(preview.silver().rows()).hasSize(4);
        assertThat(preview.rules())
                .extracting(Preview.Rule::name, Preview.Rule::failing)
                .containsExactly(tuple("has_celsius", 1L), tuple("plausible", 1L), tuple("no_poison", 0L));
        assertThat(preview.quarantine().rows()).hasSize(1);
        assertThat(preview.gold())
                .extracting(Preview.Gold::name, Preview.Gold::rows)
                .containsExactly(tuple("per_site", null), tuple("per_sensor", 3L));
        assertThat(preview.gold().getFirst().error())
                .isEqualTo("Reference sensors is not loaded yet; run the pipeline once");
    }

    @Test
    void impact_countsChangedValuesRulesAndMergedFacts() {
        PipelineSpec saved = spec(template);
        PipelineSpec draft = spec(template.replace("celsius * 9 / 5 + 32", "round(celsius * 9 / 5 + 32)")
                .replace("keys: [sensorId, readAt]", "keys: [sensorId]")
                .replace(
                        "    - { name: no_poison,",
                        "    - { name: mild, check: \"celsius < 50\", on-failure: warn }\n    - { name: no_poison,"));

        Impact impact;
        try (Sample sample = samples.take(saved, 100, RUN)) {
            impact =
                    new DraftImpact(spark, lake, new DeltaTables(spark), Steps.NONE).compare(saved, draft, sample, RUN);
        }

        assertThat(impact.columns())
                .filteredOn(change -> change.name().equals("fahrenheit"))
                .singleElement()
                .extracting(Impact.ColumnChange::changedRows)
                .isEqualTo(3L);
        assertThat(impact.columns())
                .filteredOn(change -> change.name().equals("celsius"))
                .singleElement()
                .extracting(Impact.ColumnChange::changedRows)
                .isEqualTo(0L);
        assertThat(impact.rules())
                .filteredOn(rule -> rule.name().equals("mild"))
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.failingBefore()).isNull();
                    assertThat(rule.failingAfter()).isEqualTo(1L);
                });
        assertThat(impact.keys()).isEqualTo(new Impact.Keys(true, 4, 3, 1, 0));
    }
}
