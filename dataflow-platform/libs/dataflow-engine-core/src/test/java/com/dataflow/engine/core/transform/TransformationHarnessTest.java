package com.dataflow.engine.core.transform;

import com.dataflow.engine.core.SparkTest;
import com.dataflow.transformation.DataTransformation;
import com.dataflow.transformation.NoParams;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.pow;
import static org.assertj.core.api.Assertions.assertThat;

@SparkTest
class TransformationHarnessTest {

    private final SparkSession spark;

    TransformationHarnessTest(SparkSession spark) {
        this.spark = spark;
    }

    @Test
    void aTransformationRunsOnRowsWrittenInTheTest() {
        Dataset<Row> quakes = spark.createDataFrame(
                List.of(RowFactory.create("ak1", 1.6), RowFactory.create("us2", 4.0)),
                new StructType().add("eventId", DataTypes.StringType).add("magnitude", DataTypes.DoubleType));

        TransformationHarness.Result result = TransformationHarness.of(new QuakeEnergy())
                .input("quakes", quakes)
                .param("base", 4.8)
                .asSilverStep();

        assertThat(result.passed()).isTrue();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.output()
                        .orderBy("eventId")
                        .select("energyJoules")
                        .as(Encoders.DOUBLE())
                        .collectAsList())
                .satisfies(
                        energies -> assertThat(energies.getFirst()).isCloseTo(1.585e7, Percentage.withPercentage(1)));
    }

    @Test
    void theSameChecksAsARunTellSilverFromGold() {
        Dataset<Row> quakes = spark.createDataFrame(
                List.of(RowFactory.create("ak1", 1.6)),
                new StructType().add("eventId", DataTypes.StringType).add("magnitude", DataTypes.DoubleType));
        TransformationHarness harness = TransformationHarness.of(new Counted()).input("quakes", quakes);

        assertThat(harness.asSilverStep().errors()).anyMatch(error -> error.contains("aggregate in gold"));
        assertThat(harness.asGold().passed()).isTrue();
    }

    @DataTransformation(name = "quake_energy")
    static final class QuakeEnergy implements Transformation<QuakeEnergy.In, QuakeEnergy.Params> {

        @Override
        public Dataset<Row> apply(In in, Params params) {
            return in.quakes()
                    .withColumn(
                            "energyJoules",
                            pow(lit(10.0), col("magnitude").multiply(1.5).plus(params.base())));
        }

        record In(Dataset<Row> quakes) {}

        record Params(double base) {}
    }

    @DataTransformation(name = "counted")
    static final class Counted implements Transformation<Counted.In, NoParams> {

        @Override
        public Dataset<Row> apply(In in, NoParams params) {
            return in.quakes().agg(count(lit(1)).as("quakes"));
        }

        record In(Dataset<Row> quakes) {}
    }
}
