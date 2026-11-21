package com.dataflow.spec.compile;

import com.dataflow.transformation.Transformation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransformationCompilerTest {

    private static final String ZONES = """
        package taxi;

        import com.dataflow.transformation.DataTransformation;
        import com.dataflow.transformation.Lake;
        import com.dataflow.transformation.Transformation;
        import org.apache.spark.sql.Dataset;
        import org.apache.spark.sql.Row;

        import static org.apache.spark.sql.functions.col;

        @DataTransformation(name = "zone_enrichment", version = 2)
        public final class ZoneEnrichment implements Transformation<ZoneEnrichment.In, ZoneEnrichment.Params> {

            public record In(Dataset<Row> trips) {}

            public record Params(String prefix) {}

            private final Lake lake;

            public ZoneEnrichment(Lake lake) {
                this.lake = lake;
            }

            public Lake lake() {
                return lake;
            }

            @Override
            public Dataset<Row> apply(In in, Params params) {
                return in.trips().join(lake.table("taxi_trips.reference.taxi_zones"), col("zoneId").equalTo(col("LocationID")), "left");
            }
        }
        """;

    private final TransformationCompiler compiler = new TransformationCompiler();

    @Test
    void compile_findsAnnotatedTransformations() {
        CompiledTransformations compiled = compiler.compile(
                Map.of("transformations/taxi/ZoneEnrichment.java", ZONES),
                getClass().getClassLoader());

        assertThat(compiled.byId()).containsOnlyKeys("zone_enrichment@2");
        assertThat(Transformation.class).isAssignableFrom(compiled.byId().get("zone_enrichment@2"));
    }

    @Test
    void compile_givesEachErrorItsFileAndLine() {
        String broken = ZONES.replace("return in.trips()", "return in.trip()");

        assertThatThrownBy(() -> compiler.compile(
                        Map.of("transformations/taxi/ZoneEnrichment.java", broken),
                        getClass().getClassLoader()))
                .isInstanceOfSatisfying(
                        CompilationFailedException.class,
                        failure -> assertThat(failure.diagnoses()).anySatisfy(diagnosis -> {
                            assertThat(diagnosis.path()).isEqualTo("transformations/taxi/ZoneEnrichment.java");
                            assertThat(diagnosis.line()).isEqualTo(30L);
                        }));
    }

    @Test
    void compile_reportsErrorsWithTheirLine() {
        String broken = ZONES.replace("return in.trips()", "return in.trip()");

        assertThatThrownBy(() -> compiler.compile(
                        Map.of("transformations/taxi/ZoneEnrichment.java", broken),
                        getClass().getClassLoader()))
                .isInstanceOf(CompilationFailedException.class)
                .hasMessageContaining("transformations/taxi/ZoneEnrichment.java:30:")
                .hasMessageContaining("trip()");
    }

    @Test
    void compile_refusesTwoTransformationsWithOneId() {
        String twin = ZONES.replace("class ZoneEnrichment", "class ZoneEnrichmentTwin")
                .replace("public ZoneEnrichment(Lake lake)", "public ZoneEnrichmentTwin(Lake lake)")
                .replace(
                        "ZoneEnrichment.In, ZoneEnrichment.Params", "ZoneEnrichmentTwin.In, ZoneEnrichmentTwin.Params");

        assertThatThrownBy(() -> compiler.compile(
                        Map.of(
                                "transformations/taxi/ZoneEnrichment.java", ZONES,
                                "transformations/taxi/ZoneEnrichmentTwin.java", twin),
                        getClass().getClassLoader()))
                .isInstanceOf(CompilationFailedException.class)
                .hasMessageContaining("zone_enrichment@2 is declared twice");
    }

    @Test
    void compile_withNothingToCompileReturnsAnEmptyCatalog() {
        assertThat(compiler.compile(Map.of(), getClass().getClassLoader()).byId())
                .isEmpty();
    }

    @Test
    void compile_packagesNestedRecordsAndHelpersForExecutors() throws Exception {
        CompiledTransformations compiled = compiler.compile(
                Map.of(
                        "transformations/taxi/ZoneEnrichment.java",
                        ZONES,
                        "transformations/taxi/Helper.java",
                        "package taxi; public record Helper(String value) {}"),
                getClass().getClassLoader());

        try (JarFile jar = new JarFile(compiled.artifact().toFile())) {
            assertThat(jar.stream().map(JarEntry::getName))
                    .containsExactly(
                            "taxi/Helper.class",
                            "taxi/ZoneEnrichment$In.class",
                            "taxi/ZoneEnrichment$Params.class",
                            "taxi/ZoneEnrichment.class");
        }
    }
}
