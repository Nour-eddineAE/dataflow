package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.SparkTest;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SparkTest
class RescueTest {

    private static final StructType TABLE = new StructType()
            .add("id", DataTypes.StringType)
            .add("Amount", DataTypes.IntegerType)
            .add("note", DataTypes.StringType);

    private final SparkSession spark;

    RescueTest(SparkSession spark) {
        this.spark = spark;
    }

    @Test
    void conform_keepsKnownColumns_andRescuesTheRest() {
        StructType raw = new StructType()
                .add("id", DataTypes.StringType)
                .add("amount", DataTypes.StringType)
                .add("surprise", DataTypes.StringType);
        Dataset<Row> rows = spark.createDataFrame(
                List.of(RowFactory.create("a", "12", "x"), RowFactory.create("b", "twelve", null)), raw);

        List<Row> conformed = Rescue.conform(rows, TABLE).orderBy("id").collectAsList();

        assertThat(conformed.get(0).schema().fieldNames()).containsExactly("id", "Amount", "note", "_rescued_data");
        assertThat(conformed.get(0).<Integer>getAs("Amount")).isEqualTo(12);
        assertThat(conformed.get(0).<String>getAs("note")).isNull();
        assertThat(conformed.get(0).<String>getAs("_rescued_data")).isEqualTo("{\"surprise\":\"x\"}");
        assertThat(conformed.get(1).<Integer>getAs("Amount")).isNull();
        assertThat(conformed.get(1).<String>getAs("_rescued_data")).isEqualTo("{\"amount\":\"twelve\"}");
    }

    @Test
    void conform_leavesRescuedDataNullWhenNothingIsRescued() {
        Dataset<Row> rows = spark.createDataFrame(List.of(RowFactory.create("a", 1, "n")), TABLE);

        assertThat(Rescue.conform(rows, TABLE).first().<String>getAs("_rescued_data"))
                .isNull();
    }

    @Test
    void landedName_putsTheHashBeforeTheExtension() {
        assertThat(IngestFlow.landedName("all_month.csv", "0123456789abcdef0123"))
                .isEqualTo("all_month.0123456789abcdef.csv");
        assertThat(IngestFlow.landedName("README", "0123456789abcdef0123")).isEqualTo("README.0123456789abcdef");
    }
}
