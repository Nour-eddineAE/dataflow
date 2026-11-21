import com.dataflow.transformation.DataTransformation;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Map;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.pow;
import static org.apache.spark.sql.functions.when;

@DataTransformation(name = "earthquake_energy")
public final class EarthquakeEnergy implements Transformation<EarthquakeEnergy.In, EarthquakeEnergy.Params> {

    public record In(Dataset<Row> quakes) {}

    public record Params(double base, double shallowKm, double deepKm) {}

    @Override
    public Dataset<Row> apply(In in, Params params) {
        return in.quakes().withColumns(Map.of(
                "energyJoules", pow(lit(10.0), col("magnitude").multiply(1.5).plus(params.base())),
                "depthClass", when(col("depthKm").lt(params.shallowKm()), "shallow")
                        .when(col("depthKm").lt(params.deepKm()), "intermediate")
                        .otherwise("deep")));
    }
}
