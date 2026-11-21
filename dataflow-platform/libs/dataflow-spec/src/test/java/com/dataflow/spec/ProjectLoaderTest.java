package com.dataflow.spec;

import com.dataflow.spec.TargetSpec.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectLoaderTest {

    private final ProjectLoader loader = new ProjectLoader(new PipelineSpecLoader());

    private static Path resource(String name) {
        try {
            return Path.of(Objects.requireNonNull(ProjectLoaderTest.class.getResource("/projects/" + name))
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void load_readsTheProjectAndItsPipelines() {
        Project project = loader.load(resource("valid"));

        assertThat(project.name()).isEqualTo("lab-sensors");
        assertThat(project.spec().description()).isEqualTo("Readings from the lab");
        assertThat(project.spec().targets()).containsOnlyKeys("dev", "prod");
        assertThat(project.spec().target("prod")).hasValueSatisfying(prod -> {
            assertThat(prod.mode()).isEqualTo(Mode.PRODUCTION);
            assertThat(prod.branch()).isEqualTo("main");
            assertThat(prod.compute().driverMemory()).isEqualTo("2g");
            assertThat(prod.compute().executors().max()).isEqualTo(6);
            assertThat(prod.compute().sparkConf()).containsEntry("spark.sql.shuffle.partitions", "32");
        });
        assertThat(project.spec().target("dev").orElseThrow().production()).isFalse();
        assertThat(project.spec().target("dev").orElseThrow().compute()).isEqualTo(ComputeSpec.DEFAULT);
        assertThat(project.pipelines().all()).extracting(PipelineSpec::dataset).containsExactly("sensor_readings");
    }

    @Test
    void load_acceptsAProjectWithoutPipelines() {
        assertThat(loader.load(resource("bare")).pipelines().all()).isEmpty();
    }

    @Test
    void load_reportsEveryProblemAtOnce() {
        assertThatThrownBy(() -> loader.load(resource("invalid")))
                .isInstanceOf(InvalidProjectSpecException.class)
                .hasMessageContaining("project must match")
                .hasMessageContaining("at most one target may be production, found 2")
                .hasMessageContaining("targets.prod.branch is required for a production target")
                .hasMessageContaining("targets.live.branch 'release..1' is not a valid branch name")
                .hasMessageContaining("targets.live.compute.executor-memory must be a positive size such as 512m or 4g");
    }

    @Test
    void load_namesAnUnknownKey() {
        assertThatThrownBy(() -> loader.load(resource("unknown")))
                .isInstanceOf(InvalidProjectSpecException.class)
                .hasMessageContaining("line 2: unknown key 'owner'");
    }

    @Test
    void withCompute_setsOneTargetAndKeepsTheRest() throws Exception {
        String content = Files.readString(resource("valid").resolve(ProjectSpec.FILE));
        ComputeSpec compute = new ComputeSpec(
                new ComputeSpec.Executors(null, 8), "4g", null, 2, null, Map.of("spark.sql.adaptive.enabled", "false"));

        String changed = loader.withCompute(content, "dev", compute);

        ProjectSpec spec = loader.parse(changed, ProjectSpec.FILE);
        assertThat(spec.description()).isEqualTo("Readings from the lab");
        assertThat(spec.target("dev").orElseThrow().compute()).isEqualTo(compute);
        assertThat(spec.target("prod").orElseThrow().compute().driverMemory()).isEqualTo("2g");
        assertThat(changed).contains("spark.sql.adaptive.enabled: \"false\"").doesNotStartWith("---");
        assertThat(loader.parse(loader.withCompute(changed, "dev", ComputeSpec.DEFAULT), ProjectSpec.FILE)
                        .target("dev").orElseThrow().compute())
                .isEqualTo(ComputeSpec.DEFAULT);
    }

    @Test
    void withCompute_refusesAnUnknownTargetOrABadSize() throws Exception {
        String content = Files.readString(resource("valid").resolve(ProjectSpec.FILE));

        assertThatThrownBy(() -> loader.withCompute(content, "staging", ComputeSpec.DEFAULT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no target 'staging'");
        assertThatThrownBy(() -> loader.withCompute(
                        content, "prod", new ComputeSpec(null, "lots", null, null, null, null)))
                .isInstanceOf(InvalidProjectSpecException.class)
                .hasMessageContaining("targets.prod.compute.driver-memory must be a positive size");
    }

    @Test
    void load_needsTheProjectFile(@TempDir Path empty) {
        assertThatThrownBy(() -> loader.load(empty))
                .isInstanceOf(InvalidProjectSpecException.class)
                .hasMessageContaining("no dataflow.yml at the project root");
    }
}
