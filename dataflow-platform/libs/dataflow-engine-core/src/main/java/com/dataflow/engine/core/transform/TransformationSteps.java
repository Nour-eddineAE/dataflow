package com.dataflow.engine.core.transform;

import com.dataflow.spec.ColumnSpec;
import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.StepSpec;
import com.dataflow.transformation.Transformation;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.scheduler.SparkListener;
import org.apache.spark.scheduler.SparkListenerJobStart;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.StructField;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.apache.spark.sql.functions.col;

@Slf4j
public class TransformationSteps implements Steps {

    private static final String JOB_GROUP = "spark.jobGroup.id";

    private final SparkSession spark;
    private final TransformationCatalog catalog;
    private final RunLake lake;
    private final Map<String, List<String>> warnings = Collections.synchronizedMap(new LinkedHashMap<>());

    public TransformationSteps(SparkSession spark, TransformationCatalog catalog, RunLake lake) {
        this.spark = spark;
        this.catalog = catalog;
        this.lake = lake;
    }

    private static PlanChecks.Findings inherited(Record inputs) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (java.lang.reflect.RecordComponent component : inputs.getClass().getRecordComponents()) {
            try {
                Object value = component.getAccessor().invoke(inputs);
                if (value instanceof Dataset<?> input) {
                    PlanChecks.Findings upstream =
                            PlanChecks.check(input.queryExecution().analyzed(), 0);
                    errors.addAll(upstream.errors());
                    warnings.addAll(upstream.warnings());
                }
            } catch (ReflectiveOperationException e) {
                throw new StepFailedException("Cannot read input " + component.getName(), e);
            }
        }
        return new PlanChecks.Findings(errors, warnings);
    }

    private static Dataset<Row> conform(StepSpec step, Dataset<Row> rows, Dataset<Row> output) {
        Map<String, StructField> produced = new LinkedHashMap<>();
        Arrays.stream(output.schema().fields()).forEach(field -> produced.put(field.name(), field));
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(rows.columns()));
        List<String> problems = new ArrayList<>();
        for (ColumnSpec added : step.adds()) {
            expected.add(added.name());
            StructField field = produced.get(added.name());
            DataType declared = DataType.fromDDL(added.type());
            if (field == null) {
                problems.add("declares " + added.name() + " but does not produce it");
            } else if (!field.dataType().equals(declared)) {
                problems.add(added.name() + " is " + field.dataType().simpleString() + ", declared "
                        + declared.simpleString());
            }
        }
        expected.stream()
                .filter(name -> !produced.containsKey(name))
                .filter(name ->
                        step.adds().stream().noneMatch(added -> added.name().equals(name)))
                .forEach(name -> problems.add("drops the column " + name));
        produced.keySet().stream()
                .filter(name -> !expected.contains(name))
                .forEach(name -> problems.add("produces " + name + ", which is not declared under adds"));
        if (!problems.isEmpty()) {
            throw new StepFailedException(
                    "Step " + step.name() + " (" + step.transformation() + "): " + String.join("; ", problems));
        }
        return output.select(
                expected.stream().map(name -> col("`" + name + "`")).toArray(Column[]::new));
    }

    @Override
    public Dataset<Row> apply(PipelineSpec plan, Dataset<Row> rows) {
        Dataset<Row> current = rows;
        for (StepSpec step : plan.silver().steps()) {
            current = run(step, current);
        }
        return current;
    }

    public Map<String, List<String>> warnings() {
        synchronized (warnings) {
            return Map.copyOf(warnings);
        }
    }

    public Map<String, String> reads() {
        return lake.reads();
    }

    @Override
    public Dataset<Row> gold(PipelineSpec plan, GoldSpec gold, Function<String, Dataset<Row>> sources) {
        return execute(gold.asStep(), sources, PlanChecks.Placement.GOLD);
    }

    private Dataset<Row> run(StepSpec step, Dataset<Row> rows) {
        return conform(
                step, rows, execute(step, source -> lake.source(source, rows), PlanChecks.Placement.SILVER));
    }

    private Dataset<Row> execute(
            StepSpec step, Function<String, Dataset<Row>> sources, PlanChecks.Placement placement) {
        if (!catalog.ids().contains(step.transformation())) {
            throw StepFailedException.of(step, "no transformation " + step.transformation()
                    + " in this release; found " + new java.util.TreeSet<>(catalog.ids()));
        }
        Transformation<?, ?> transformation = catalog.get(step.transformation(), lake);
        Record inputs = Binding.inputs(transformation, step, sources);
        Record params = Binding.params(transformation, step);
        String group = "step-" + step.name() + "-" + UUID.randomUUID();
        AtomicLong jobs = new AtomicLong();
        SparkListener counter = new SparkListener() {
            @Override
            public void onJobStart(SparkListenerJobStart start) {
                if (start.properties() != null
                        && group.equals(start.properties().getProperty(JOB_GROUP))) {
                    jobs.incrementAndGet();
                }
            }
        };
        spark.sparkContext().addSparkListener(counter);
        spark.sparkContext().setJobGroup(group, "transformation step " + step.name(), false);
        Dataset<Row> output;
        try {
            output = Binding.apply(transformation, inputs, params);
        } catch (RuntimeException e) {
            throw new StepFailedException(
                    "Step " + step.name() + " (" + step.transformation() + ") failed: " + e.getMessage(), e);
        } finally {
            spark.sparkContext().clearJobGroup();
            spark.sparkContext().removeSparkListener(counter);
        }
        if (output == null) {
            throw StepFailedException.of(step, step.transformation() + " returned no Dataset");
        }
        PlanChecks.Findings findings = PlanChecks.check(output.queryExecution().analyzed(), jobs.get(), placement)
                .without(inherited(inputs));
        if (findings.failed()) {
            throw StepFailedException.of(step, String.join("; ", findings.errors()));
        }
        if (!findings.warnings().isEmpty()) {
            warnings.put(step.name(), findings.warnings());
            findings.warnings().forEach(warning -> log.warn("Step {}: {}", step.name(), warning));
        }
        return output;
    }
}
