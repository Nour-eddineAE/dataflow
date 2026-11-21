package com.dataflow.engine.core.run;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngineRuntimeTest {

    @Test
    void namesEveryVersionARunUses() {
        EngineRuntime runtime = EngineRuntime.of("3.0.0", "4.2.0");

        assertThat(runtime.dataflow()).isEqualTo("3.0.0");
        assertThat(runtime.spark()).isEqualTo("4.2.0");
        assertThat(runtime.delta()).matches("\\d+\\.\\d+\\.\\d+");
        assertThat(runtime.scala()).startsWith("2.13.");
        assertThat(runtime.java()).startsWith("21");
        assertThat(runtime.labels()).containsOnlyKeys("dataflow", "spark", "delta", "scala", "java");
    }

    @Test
    void aMissingVersionIsNamedUnknown() {
        EngineRuntime runtime = EngineRuntime.of(null, " ");

        assertThat(runtime.dataflow()).isEqualTo(EngineRuntime.UNKNOWN);
        assertThat(runtime.spark()).isEqualTo(EngineRuntime.UNKNOWN);
    }
}
