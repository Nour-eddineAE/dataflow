import {NAME} from './names';

export const TRANSFORMATION_NAME = new RegExp(`^${NAME}$`);

export function className(name: string) {
    return name.split('_').filter(Boolean).map((part) => part[0].toUpperCase() + part.slice(1)).join('');
}

export function transformationTemplate(name: string) {
    const type = className(name);
    return `import com.dataflow.transformation.DataTransformation;
import com.dataflow.transformation.NoParams;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.col;

@DataTransformation(name = "${name}")
public final class ${type} implements Transformation<${type}.In, NoParams> {

    public record In(Dataset<Row> rows) {}

    @Override
    public Dataset<Row> apply(In in, NoParams params) {
        return in.rows();
    }
}
`;
}
