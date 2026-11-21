package com.dataflow.engine.cli;

import com.dataflow.engine.config.EngineProperties;
import com.dataflow.engine.core.events.EventLog;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.run.Batching;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.engine.core.transform.RunLake;
import com.dataflow.engine.transform.Transformations;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecs;
import com.dataflow.spec.lake.Lake;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
class RunContexts {

    private final PipelineSpecs plans;
    private final ObjectProvider<SparkSession> spark;
    private final Lake lake;
    private final ObjectProvider<DeltaTables> tables;
    private final Clock clock;
    private final EngineProperties properties;
    private final Transformations transformations;

    RunContext create(PipelineSpec plan, String runId, String partition) {
        SparkSession session = spark.getObject();
        RunLake reads = new RunLake(session, lake, plan.dataset());
        return new RunContext(
                session,
                lake,
                tables.getObject(),
                new EventLog(session, lake),
                plans,
                plan,
                runId,
                partition,
                clock,
                new Batching(
                        properties.batching().maxRecordsPerFile(),
                        properties.batching().maxFilesPerBatch()),
                reads,
                transformations.steps(session, reads, plan.usesTransformations()));
    }
}
