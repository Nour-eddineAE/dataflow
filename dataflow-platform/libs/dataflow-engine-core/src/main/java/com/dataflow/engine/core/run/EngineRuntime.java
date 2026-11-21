package com.dataflow.engine.core.run;

import io.delta.tables.DeltaTable;
import scala.util.Properties;

import java.util.Map;
import java.util.Optional;

public record EngineRuntime(String dataflow, String spark, String delta, String scala, String java) {

    public static final String UNKNOWN = "unknown";

    public static EngineRuntime of(String dataflow, String spark) {
        return new EngineRuntime(
                known(dataflow),
                known(spark),
                known(DeltaTable.class.getPackage().getImplementationVersion()),
                Properties.versionNumberString(),
                known(System.getProperty("java.version")));
    }

    public Map<String, String> labels() {
        return Map.of("dataflow", dataflow, "spark", spark, "delta", delta, "scala", scala, "java", java);
    }

    private static String known(String value) {
        return Optional.ofNullable(value).filter(text -> !text.isBlank()).orElse(UNKNOWN);
    }
}
