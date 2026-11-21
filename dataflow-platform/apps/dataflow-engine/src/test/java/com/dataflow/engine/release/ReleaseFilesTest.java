package com.dataflow.engine.release;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.ProjectLoader;
import com.dataflow.spec.ReleaseId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseFilesTest {

    private static final String COMPLETE = "a41c9e2f6b1d4e8a9c3b2f1e0d9c8b7a6f5e4d3c";
    private static final String INCOMPLETE = "b52d0f3a7c2e5f9b0d4c3a2f1e0d9c8b7a6f5e4d";

    private final ReleaseFiles files = new ReleaseFiles(root(), Map.of("spark.master", "local[1]"));

    private static String root() {
        try {
            Path path = Path.of(Objects.requireNonNull(ReleaseFilesTest.class.getResource("/releases"))
                    .toURI());
            assertThat(Files.isDirectory(path)).isTrue();
            return path.toUri().toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void materialize_copiesTheReleaseToALocalFolder() {
        Path folder = files.materialize(new ReleaseId("lab-sensors", COMPLETE));

        assertThat(folder.getFileName()).hasToString("lab-sensors");
        assertThat(folder.resolve("pipelines/sensor_readings/pipeline.yml")).isRegularFile();
        assertThat(new ProjectLoader(new PipelineSpecLoader())
                        .load(folder)
                        .pipelines()
                        .all())
                .extracting(PipelineSpec::dataset)
                .containsExactly("sensor_readings");
    }

    @Test
    void materialize_refusesAReleaseWithoutItsManifest() {
        assertThatThrownBy(() -> files.materialize(new ReleaseId("lab-sensors", INCOMPLETE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is missing or incomplete: no release.json");
    }

    @Test
    void activeRelease_readsTheOptionAndLabelsTheRun() {
        ActiveRelease release = ActiveRelease.of(new DefaultApplicationArguments(
                "run", "--dataset=sensor_readings", "--release=lab-sensors@" + COMPLETE));

        assertThat(release.id()).hasValue(new ReleaseId("lab-sensors", COMPLETE));
        assertThat(release.labels()).containsEntry("release", "lab-sensors@" + COMPLETE);
        assertThat(ActiveRelease.of(new DefaultApplicationArguments("run")).labels())
                .isEmpty();
    }
}
