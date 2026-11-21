package com.dataflow.engine.core.run;

import com.dataflow.engine.core.events.EventLog;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.transform.RunLake;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecs;
import com.dataflow.spec.lake.Lake;
import org.apache.spark.sql.SparkSession;

import java.time.Clock;

public record RunContext(
        SparkSession spark,
        Lake lake,
        DeltaTables tables,
        EventLog events,
        PipelineSpecs plans,
        PipelineSpec plan,
        String runId,
        String partition,
        Clock clock,
        Batching batching,
        RunLake reads,
        Steps steps) {

    public String dataset() {
        return plan.dataset();
    }
}
