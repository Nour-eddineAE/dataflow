import com.dataflow.transformation.DataTransformation;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.max;
import static org.apache.spark.sql.functions.round;
import static org.apache.spark.sql.functions.sum;

@DataTransformation(name = "region_energy")
public final class RegionEnergy implements Transformation<RegionEnergy.In, RegionEnergy.Params> {

    public record In(Dataset<Row> events) {}

    public record Params(double minMagnitude) {}

    @Override
    public Dataset<Row> apply(In in, Params params) {
        in.events().show(10, false);
        return in.events()
                .where(col("magnitude").geq(params.minMagnitude()))
                .groupBy(coalesce(col("region"), lit("Unknown")).as("region"), col("depthClass"))
                .agg(count(lit(1)).as("events"),
                        round(max(col("magnitude")), 1).as("maxMagnitude"),
                        sum(col("energyJoules")).as("energyJoules"));
    }
}
