package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.SparkTest;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SparkTest
class SourceInferenceTest {

    private final SparkSession spark;

    SourceInferenceTest(SparkSession spark) {
        this.spark = spark;
    }

    private static Path file(String name) {
        try {
            return Path.of(Objects.requireNonNull(SourceInferenceTest.class.getResource("/sources/" + name))
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void anEarthquakeFeedBecomesAValidSpecWithItsReasons() {
        SourceLocation location = SourceLocation.analyze("https://earthquake.usgs.gov/feed/v1.0/summary/all_month.csv");
        Inference inference =
                new SourceInference().infer(spark, location, file("all_month.csv"), "quakes", 1000);

        assertThat(inference.problems()).isEmpty();
        assertThat(inference.format()).isEqualTo("csv");
        assertThat(inference.sampledRows()).isEqualTo(5);
        assertThat(inference.keys()).containsExactly("id");
        assertThat(inference.sequenceBy()).isEqualTo("updated");
        assertThat(inference.columns())
                .filteredOn(column -> column.name().equals("time"))
                .singleElement()
                .satisfies(column -> {
                    assertThat(column.type()).isEqualTo("timestamp");
                    assertThat(column.from()).isEqualTo("to_timestamp(time)");
                });
        assertThat(inference.columns())
                .filteredOn(column -> column.name().equals("magType"))
                .singleElement()
                .satisfies(column -> assertThat(column.distinct()).isEqualTo(3));
        assertThat(inference.schedule()).isEqualTo("0 */6 * * *");
        assertThat(inference.spec())
                .contains("check: \"mag BETWEEN")
                .contains("check: \"depth BETWEEN 0 AND 44\"")
                .contains("known_status")
                .contains("SELECT to_date(time) AS day, magType, count(*) AS rows");
        assertThat(inference.reasons())
                .extracting(Inference.Reason::choice)
                .contains("keys: id", "sequence-by: updated", "time: timestamp");

        PipelineSpec spec = new PipelineSpecLoader().parse(inference.spec(), "quakes");
        assertThat(spec.history().trackHistoryExcept()).containsExactly("updated");
    }

    @Test
    void aDateInTheFileNameBecomesThePartition() {
        SourceLocation monthly = SourceLocation.analyze(
                "https://d37ci6vzurychx.cloudfront.net/trip-data/yellow_tripdata_2024-01.parquet");
        SourceLocation daily = SourceLocation.analyze("/data/sensors/readings_2026-03-04.csv");

        assertThat(monthly.template())
                .isEqualTo("https://d37ci6vzurychx.cloudfront.net/trip-data/yellow_tripdata_{partition}.parquet");
        assertThat(monthly.partitionKind()).isEqualTo("monthly");
        assertThat(monthly.partitionStart()).isEqualTo("2024-01");
        assertThat(monthly.format()).isEqualTo("parquet");
        assertThat(monthly.datasetName()).isEqualTo("yellow_tripdata");
        assertThat(daily.partitionKind()).isEqualTo("daily");
        assertThat(daily.template()).isEqualTo("/data/sensors/readings_{partition}.csv");
        assertThat(SourceLocation.analyze("https://x.org/api/export").format()).isNull();
    }
}
