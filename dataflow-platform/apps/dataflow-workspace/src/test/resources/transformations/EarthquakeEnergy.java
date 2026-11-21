import com.dataflow.transformation.DataTransformation;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

@DataTransformation(name = "earthquake_energy", version = 2)
public final class EarthquakeEnergy implements Transformation<EarthquakeEnergy.In, EarthquakeEnergy.Params> {

    public record In(Dataset<Row> quakes, Dataset<Row> zones) {}

    public record Params(double base, int shallowKm, boolean strict, String label) {}

    @Override
    public Dataset<Row> apply(In in, Params params) {
        return in.quakes();
    }
}
