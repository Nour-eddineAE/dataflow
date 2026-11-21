package com.dataflow.engine.core.quality;

import com.dataflow.spec.spark.StageDataset;
import com.dataflow.spec.stage.Silver;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Map;

public record ExpectationResult(
        StageDataset<Silver> kept, Dataset<Row> quarantined, long evaluated, long dropped, Map<String, Long> failures) {

    public long passed() {
        return evaluated - dropped;
    }
}
