package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.run.AssetKeys;
import com.dataflow.engine.core.run.Flow;
import com.dataflow.engine.core.run.FlowResult;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.spec.ReferenceSpec;
import com.dataflow.spec.lake.FlowNames;
import com.dataflow.spec.lake.Layer;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;

import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@SuppressWarnings("resource")
public class ReferencesFlow implements Flow {

    private final Sources sources;

    @Override
    public String name() {
        return FlowNames.REFERENCES;
    }

    @Override
    public Set<String> inputs() {
        return Set.of();
    }

    @Override
    public FlowResult run(RunContext context) {
        FlowResult result = new FlowResult();
        for (ReferenceSpec reference : context.plan().references()) {
            String location = reference.path() != null
                    ? reference.path()
                    : sources.fetch(context.dataset(), reference, true).toUri().toString();
            Dataset<Row> rows = context.spark()
                    .read()
                    .format(reference.format())
                    .options(reference.options())
                    .load(location);
            String table = context.lake().table(Layer.REFERENCE, context.dataset(), reference.name());
            rows.write()
                    .format(DeltaTables.FORMAT)
                    .mode(SaveMode.Overwrite)
                    .option("overwriteSchema", true)
                    .save(table);
            long count = context.spark().read().format(DeltaTables.FORMAT).load(table).count();
            result.rows(count, count, 0)
                    .materialized(
                            AssetKeys.of(context.dataset(), Layer.REFERENCE, reference.name()),
                            Map.of("rows", count, "table", table));
        }
        return result;
    }
}
