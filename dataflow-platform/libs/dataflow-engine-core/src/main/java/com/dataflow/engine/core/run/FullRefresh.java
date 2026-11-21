package com.dataflow.engine.core.run;

import com.dataflow.spec.lake.FlowNames;
import com.dataflow.spec.lake.Layer;
import org.apache.hadoop.fs.Path;
import org.apache.spark.sql.delta.DeltaLog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public final class FullRefresh {

    private static final List<Layer> TABLES = List.of(Layer.BRONZE, Layer.SILVER, Layer.QUARANTINE, Layer.HISTORY);
    private static final List<String> STREAMS = List.of(FlowNames.BRONZE, FlowNames.SILVER, FlowNames.HISTORY);

    private FullRefresh() {}

    public static void reset(RunContext context) {
        String dataset = context.dataset();
        TABLES.forEach(layer -> delete(context, context.lake().table(layer, dataset)));
        STREAMS.forEach(flow -> delete(context, context.lake().checkpoint(dataset, flow)));
        DeltaLog.clearCache();
    }

    private static void delete(RunContext context, String location) {
        Path path = new Path(location);
        try {
            path.getFileSystem(context.spark().sparkContext().hadoopConfiguration()).delete(path, true);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot remove " + location, e);
        }
    }
}
