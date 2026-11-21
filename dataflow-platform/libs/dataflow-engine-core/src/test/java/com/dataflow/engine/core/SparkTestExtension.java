package com.dataflow.engine.core;

import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

public class SparkTestExtension implements BeforeAllCallback, ParameterResolver {

    static SparkSession sharedSession() {
        return SparkSession.builder()
                .master("local[2]")
                .appName("dataflow-engine-test")
                .config("spark.sql.session.timeZone", "UTC")
                .config("spark.ui.enabled", "false")
                .config("spark.sql.shuffle.partitions", "2")
                .config("spark.databricks.delta.snapshotPartitions", "2")
                .config("spark.databricks.delta.properties.defaults.enableDeletionVectors", "false")
                .config("spark.databricks.delta.retentionDurationCheck.enabled", "false")
                .config("spark.scheduler.mode", "FAIR")
                .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
                .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")
                .getOrCreate();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        sharedSession();
    }

    @Override
    public boolean supportsParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        return paramCtx.getParameter().getType() == SparkSession.class;
    }

    @Override
    public Object resolveParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        return sharedSession();
    }
}
