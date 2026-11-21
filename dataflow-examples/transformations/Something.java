import com.dataflow.transformation.DataTransformation;
import com.dataflow.transformation.NoParams;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.col;

@DataTransformation(name = "something")
public final class Something implements Transformation<Something.In, NoParams> {

    public record In(Dataset<Row> rows) {}

    @Override
    public Dataset<Row> apply(In in, NoParams params) {
        System.out.println("this is so stupid"); 
        return in.rows();
    }
}
