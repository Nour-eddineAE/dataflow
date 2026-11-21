package com.dataflow.engine.core.events;

import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.spec.lake.Lake;
import io.delta.tables.DeltaTable;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.max;

@RequiredArgsConstructor
public class EventLog {

    public static final StructType SCHEMA = new StructType()
            .add("event_id", DataTypes.StringType, false)
            .add("ts", DataTypes.TimestampType, false)
            .add("run_id", DataTypes.StringType, false)
            .add("dataset", DataTypes.StringType, false)
            .add("plan_version", DataTypes.IntegerType)
            .add("trigger", DataTypes.StringType)
            .add("partition", DataTypes.StringType)
            .add("event_type", DataTypes.StringType, false)
            .add("flow", DataTypes.StringType)
            .add("status", DataTypes.StringType, false)
            .add("started_at", DataTypes.TimestampType)
            .add("ended_at", DataTypes.TimestampType)
            .add("rows_in", DataTypes.LongType)
            .add("rows_out", DataTypes.LongType)
            .add("rows_dropped", DataTypes.LongType)
            .add("details", DataTypes.StringType);

    private final SparkSession spark;
    private final Lake lake;

    private static Row row(RunEvent event) {
        return RowFactory.create(
                event.eventId(),
                timestamp(event.ts()),
                event.runId(),
                event.dataset(),
                event.planVersion(),
                event.trigger(),
                event.partition(),
                event.eventType(),
                event.flow(),
                event.status(),
                timestamp(event.startedAt()),
                timestamp(event.endedAt()),
                event.rowsIn(),
                event.rowsOut(),
                event.rowsDropped(),
                event.details());
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public synchronized void record(RunEvent... events) {
        if (events.length == 0) {
            return;
        }
        List<Row> rows = Arrays.stream(events).map(EventLog::row).toList();
        spark.createDataFrame(rows, SCHEMA)
                .coalesce(1)
                .write()
                .format(DeltaTables.FORMAT)
                .mode(SaveMode.Append)
                .option(DeltaTables.DELETION_VECTORS, "false")
                .save(lake.events(events[0].dataset()));
    }

    public Optional<Instant> lastCompleted(String dataset, String eventType) {
        String path = lake.events(dataset);
        if (!DeltaTable.isDeltaTable(spark, path)) {
            return Optional.empty();
        }
        Row last = spark.read()
                .format(DeltaTables.FORMAT)
                .load(path)
                .where(col("event_type").equalTo(eventType).and(col("status").equalTo("COMPLETED")))
                .agg(max("ts"))
                .first();
        return last.isNullAt(0)
                ? Optional.empty()
                : Optional.of(last.getTimestamp(0).toInstant());
    }
}
