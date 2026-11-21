package com.dataflow.engine.transform;

import com.dataflow.spec.compile.CompiledTransformations;
import com.dataflow.spec.compile.TransformationCompiler;
import com.dataflow.transformation.Lake;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringTransformationCatalogTest {

    private static final String SOURCE = """
        package taxi;

        import com.dataflow.transformation.DataTransformation;
        import com.dataflow.transformation.Lake;
        import com.dataflow.transformation.NoParams;
        import com.dataflow.transformation.Transformation;
        import org.apache.spark.sql.Dataset;
        import org.apache.spark.sql.Row;

        @DataTransformation(name = "passthrough")
        public final class Passthrough implements Transformation<Passthrough.In, NoParams> {

            public record In(Dataset<Row> rows) {}

            private final Lake lake;

            public Passthrough(Lake lake) {
                this.lake = lake;
            }

            public Lake lake() {
                return lake;
            }

            @Override
            public Dataset<Row> apply(In in, NoParams params) {
                return in.rows();
            }
        }
        """;

    @Test
    void get_buildsTheBeanInAChildContextWithTheRunsLake() throws Exception {
        CompiledTransformations compiled = new TransformationCompiler()
                .compile(
                        Map.of("transformations/Passthrough.java", SOURCE),
                        getClass().getClassLoader());
        Lake lake = new Lake() {
            @Override
            public Dataset<Row> table(String name) {
                return null;
            }

            @Override
            public Dataset<Row> files(String path, String format, Map<String, String> options) {
                return null;
            }
        };
        try (GenericApplicationContext parent = new GenericApplicationContext()) {
            parent.refresh();
            try (SpringTransformationCatalog catalog = new SpringTransformationCatalog(parent, compiled)) {
                Transformation<?, ?> transformation = catalog.get("passthrough@1", lake);

                assertThat(transformation.getClass().getMethod("lake").invoke(transformation))
                        .isSameAs(lake);
                assertThat(catalog.ids()).containsExactly("passthrough@1");
            }
        }
    }
}
