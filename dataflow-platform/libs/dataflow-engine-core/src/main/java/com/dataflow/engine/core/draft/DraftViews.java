package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.flows.HistoryFlow;
import com.dataflow.engine.core.gold.GoldPlanner;
import com.dataflow.engine.core.gold.GoldStepException;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.transform.RunLake;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.TableRef;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.lake.Layer;
import com.dataflow.spec.spark.StageDataset;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.Optional;

@RequiredArgsConstructor
class DraftViews {

    private final SparkSession spark;
    private final Lake lake;
    private final DeltaTables tables;

    GoldPlanner planner(PipelineSpec spec, Dataset<Row> silver, Steps steps) {
        return new GoldPlanner(spark, spec, new Relations(spec, silver, new RunLake(spark, lake, spec.dataset())), steps);
    }

    private final class Relations implements GoldPlanner.Relations {

        private final PipelineSpec spec;
        private final Dataset<Row> silver;
        private final RunLake reads;

        private Relations(PipelineSpec spec, Dataset<Row> silver, RunLake reads) {
            this.spec = spec;
            this.silver = silver;
            this.reads = reads;
        }

        @Override
        public Dataset<Row> current() {
            if (silver == null) {
                throw new GoldStepException(spec.dataset() + " has no source, so no current table");
            }
            return StageDataset.conform(HistoryFlow.versions(silver, spec), spec.currentSchema())
                    .dataset();
        }

        @Override
        public Dataset<Row> reference(String name) {
            String table = lake.table(Layer.REFERENCE, spec.dataset(), name);
            if (!tables.exists(table)) {
                throw new GoldStepException("Reference " + name + " is not loaded yet; run the pipeline once");
            }
            return spark.read().format(DeltaTables.FORMAT).load(table);
        }

        @Override
        public Dataset<Row> input(TableRef table) {
            if (!reads.exists(table.toString())) {
                throw new GoldStepException("Input " + table + " has no data yet; run " + table.dataset() + " once");
            }
            return reads.table(table.toString());
        }

        @Override
        public Optional<Dataset<Row>> stored(String table) {
            return Optional.empty();
        }
    }
}
