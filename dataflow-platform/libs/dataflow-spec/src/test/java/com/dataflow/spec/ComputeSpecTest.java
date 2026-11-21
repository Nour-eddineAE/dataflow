package com.dataflow.spec;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComputeSpecTest {

    private final PipelineSpecLoader loader = new PipelineSpecLoader();

    private static String spec() throws Exception {
        return Files.readString(Path.of("src/test/resources/plans/valid/sensor_readings/pipeline.yml")) + "\n";
    }

    @Test
    void existingPipelinesKeepClusterDefaults() throws Exception {
        assertThat(loader.parse(spec(), "test").compute()).isEqualTo(ComputeSpec.DEFAULT);
    }

    @Test
    void aPipelineCanSetResourcesAndSqlTuning() throws Exception {
        PipelineSpec plan = loader.parse(spec() + """
            compute:
              executors: { min: 0, max: 8 }
              executor-memory: 4g
              driver-memory: 2g
              driver-cores: 1
              executor-cores: 2
              spark-conf:
                spark.sql.shuffle.partitions: "64"
                spark.sql.adaptive.enabled: "true"
                spark.sql.autoBroadcastJoinThreshold: "32mb"
            """, "test");

        assertThat(plan.compute().executors()).isEqualTo(new ComputeSpec.Executors(0, 8));
        assertThat(plan.compute().executorMemory()).isEqualTo("4g");
        assertThat(plan.compute().sparkConf()).containsEntry("spark.sql.shuffle.partitions", "64");
    }

    @Test
    void aPipelineCanTurnItsSparkEventLogOnOrOff() throws Exception {
        PipelineSpec plan = loader.parse(spec() + """
            compute:
              spark-conf:
                spark.eventLog.enabled: "true"
            """, "test");

        assertThat(plan.compute().sparkConf()).containsEntry("spark.eventLog.enabled", "true");
        assertThatThrownBy(() -> loader.parse(spec() + """
            compute:
              spark-conf:
                spark.eventLog.enabled: "yes"
            """, "test"))
                .hasMessageContaining("invalid value for spark.eventLog.enabled");
    }

    @Test
    void credentialsAndExecutionOverridesAreRefusedAlongsideInvalidResources() throws Exception {
        String content = spec() + """
            compute:
              executors: { min: 9, max: 2 }
              executor-memory: -4g
              driver-cores: 0
              spark-conf:
                spark.master: local[*]
                spark.hadoop.fs.s3a.secret.key: hidden
                spark.sql.shuffle.partitions: "0"
                spark.sql.adaptive.enabled: maybe
            """;

        assertThatThrownBy(() -> loader.parse(content, "test"))
                .isInstanceOf(InvalidPipelineSpecException.class)
                .hasMessageContaining("min must not exceed max")
                .hasMessageContaining("executor-memory must be a positive size")
                .hasMessageContaining("driver-cores must be 1 or more")
                .hasMessageContaining("setting is not allowed: spark.master")
                .hasMessageContaining("setting is not allowed: spark.hadoop.fs.s3a.secret.key")
                .hasMessageContaining("invalid value for spark.sql.shuffle.partitions")
                .hasMessageNotContaining("hidden");
    }
}
