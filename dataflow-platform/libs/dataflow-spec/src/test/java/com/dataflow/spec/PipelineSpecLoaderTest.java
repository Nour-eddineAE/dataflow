package com.dataflow.spec;

import com.dataflow.spec.BronzeSpec.SchemaEvolution;
import com.dataflow.spec.ExpectationSpec.OnFailure;
import com.dataflow.spec.PartitionsSpec.Kind;
import com.dataflow.spec.ServeSpec.Mode;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineSpecLoaderTest {

    private final PipelineSpecLoader loader = new PipelineSpecLoader();

    static Path resource(String name) {
        try {
            return Path.of(Objects.requireNonNull(PipelineSpecLoaderTest.class.getResource("/plans/" + name))
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void loadAll_readsEveryFieldOfAValidPlan() {
        PipelineSpec plan = loader.loadAll(resource("valid")).get("sensor_readings");

        assertThat(plan.version()).isEqualTo(3);
        assertThat(plan.owner()).isEqualTo("lab");
        assertThat(plan.schedule()).isEqualTo("15 * * * *");
        assertThat(plan.source().options()).containsEntry("header", "true");
        assertThat(plan.source().partitions().kind()).isEqualTo(Kind.DAILY);
        assertThat(plan.source().partitions().start()).isEqualTo("2026-01-01");
        assertThat(plan.bronze().schemaEvolution()).isEqualTo(SchemaEvolution.ADD_NEW_COLUMNS);
        assertThat(plan.bronze().maxFilesPerTrigger()).isEqualTo(2);
        assertThat(plan.silver().expectations())
                .extracting(ExpectationSpec::onFailure)
                .containsExactly(OnFailure.DROP, OnFailure.WARN);
        assertThat(plan.history().keys()).containsExactly("sensorId", "readAt");
        assertThat(plan.history().sequenceBy()).isEqualTo("readAt");
        assertThat(plan.references())
                .singleElement()
                .satisfies(r -> assertThat(r.name()).isEqualTo("sensors"));
        assertThat(plan.serve()).extracting(ServeSpec::mode).containsExactly(Mode.VIEW, Mode.COPY);
        assertThat(plan.serve().get(1).goldName()).isEqualTo("daily_average");
        assertThat(plan.quarantines()).isTrue();
    }

    @Test
    void schemas_followTheDeclaredColumnsThenTheSystemColumns() {
        PipelineSpec plan = loader.loadAll(resource("valid")).get("sensor_readings");

        StructType facts = plan.factSchema();
        assertThat(facts.fieldNames())
                .containsExactly("sensorId", "readAt", "celsius", "fahrenheit", "factUniqueId", "processedAt");
        assertThat(facts.apply("readAt").dataType()).isEqualTo(DataTypes.TimestampNTZType);
        assertThat(facts.apply("celsius").dataType()).isEqualTo(DataTypes.createDecimalType(5, 2));
        assertThat(facts.apply("sensorId").nullable()).isFalse();
        assertThat(plan.silverSchema().fieldNames()).endsWith("_source_file", "_ingested_at", "_run_id");
        assertThat(plan.currentSchema().fieldNames()).endsWith("processedAt", "__START_AT");
        assertThat(plan.changeColumns()).containsExactly("sensorId", "readAt");
    }

    @Test
    void forPartition_fillsThePlaceholder() {
        SourceSpec source =
                loader.loadAll(resource("valid")).get("sensor_readings").source();

        assertThat(source.forPartition("2026-03-04").path()).isEqualTo("/data/sensors/readings_2026-03-04.csv");
    }

    @Test
    void load_reportsEveryProblemAtOnce() {
        assertThatThrownBy(() -> loader.loadAll(resource("invalid")))
                .isInstanceOf(InvalidPipelineSpecException.class)
                .hasMessageContaining("version must be 3 or more")
                .hasMessageContaining("schedule must be a five-field cron expression")
                .hasMessageContaining("source needs exactly one of url and path")
                .hasMessageContaining("source.partitions.monthly.start '2024' does not match")
                .hasMessageContaining("silver.columns.id.type: cannot parse 'strng'")
                .hasMessageContaining("silver.columns._secret is reserved by the platform")
                .hasMessageContaining("silver.columns.amount.from: cannot parse 'amount +* 2'")
                .hasMessageContaining("column 'amount' is declared twice")
                .hasMessageContaining("expectation 'positive' is declared twice")
                .hasMessageContaining(
                        "expectation positive: cannot parse 'amount <': Syntax error at or near end of input (line 1,"
                                + " pos 8)")
                .hasMessageContaining("history key 'missing' is not declared")
                .hasMessageContaining("history.sequence-by column 'label' must be a timestamp, date or bigint")
                .hasMessageContaining("gold totals: cannot parse")
                .hasMessageContaining("serve.from 'gold.nothing' must be one of [current, gold.totals]")
                .hasMessageContaining("serve copy is a copy and needs order-by");
    }

    @Test
    void load_rejectsUnknownKeys() {
        assertThatThrownBy(() -> loader.loadAll(resource("typo")))
                .isInstanceOf(InvalidPipelineSpecException.class)
                .hasMessageContaining("sequence_by");
    }

    @Test
    void parse_listsEveryProblemOfADraft() throws Exception {
        String draft = Files.readString(resource("invalid").resolve("broken").resolve(PipelineSpecLoader.PLAN_FILE));

        assertThatThrownBy(() -> loader.parse(draft, "draft"))
                .isInstanceOfSatisfying(
                        InvalidPipelineSpecException.class,
                        e -> assertThat(e.problems())
                                .hasSizeGreaterThan(10)
                                .contains("version must be 3 or more", "column 'amount' is declared twice"));
    }

    @Test
    void parse_namesTheLineOfAYamlError() throws Exception {
        String draft = Files.readString(resource("typo").resolve("typo").resolve(PipelineSpecLoader.PLAN_FILE));

        assertThatThrownBy(() -> loader.parse(draft, "draft"))
                .isInstanceOfSatisfying(
                        InvalidPipelineSpecException.class,
                        e -> assertThat(e.problems())
                                .singleElement()
                                .asString()
                                .isEqualTo("line 9: unknown key 'sequence_by', expected one of [keep-closed-days, keys,"
                                        + " sequence-by, stored-as, track-history-except]"));
    }

    @Test
    void parse_namesTheLineOfAnUnknownKey() throws Exception {
        String topLevel =
                Files.readString(resource("unknown").resolve("top_level").resolve(PipelineSpecLoader.PLAN_FILE));
        String nested = Files.readString(resource("unknown").resolve("nested").resolve(PipelineSpecLoader.PLAN_FILE));

        assertThatThrownBy(() -> loader.parse(topLevel, "draft"))
                .isInstanceOfSatisfying(
                        InvalidPipelineSpecException.class,
                        e -> assertThat(e.problems())
                                .singleElement()
                                .asString()
                                .startsWith(
                                        "line 3: unknown key 'retries', expected one of [bronze, compute, dataset,"));
        assertThatThrownBy(() -> loader.parse(nested, "draft"))
                .isInstanceOfSatisfying(
                        InvalidPipelineSpecException.class,
                        e -> assertThat(e.problems())
                                .singleElement()
                                .asString()
                                .isEqualTo("line 7: unknown key 'form', expected one of [from, name, required, type]"));
    }

    @Test
    void parse_rejectsAnEmptyDraft() {
        assertThatThrownBy(() -> loader.parse("", "draft"))
                .isInstanceOfSatisfying(
                        InvalidPipelineSpecException.class,
                        e -> assertThat(e.problems()).containsExactly("the spec is empty"));
    }

    @Test
    void loadAll_requiresTheFolderToBeNamedAfterTheDataset() {
        assertThatThrownBy(() -> loader.loadAll(resource("misplaced")))
                .isInstanceOf(InvalidPipelineSpecException.class)
                .hasMessageContaining("dataset 'right_name' must match its folder name 'wrong_folder'");
    }

    @Test
    void loadAll_rejectsTwoPlansServingOneTable() {
        assertThatThrownBy(() -> loader.loadAll(resource("clash")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'shared' is served by both first and second");
    }

    @Test
    void get_namesTheKnownDatasets() {
        PipelineSpecs plans = loader.loadAll(resource("valid"));

        assertThatThrownBy(() -> plans.get("nope"))
                .isInstanceOf(UnknownDatasetException.class)
                .hasMessageContaining("[sensor_readings]");
    }

    @Test
    void loadAll_failsWithoutAnyPlan() {
        assertThatThrownBy(() -> loader.loadAll(resource("valid").resolve("sensor_readings")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No pipeline.yml found");
    }

    @Test
    void steps_addTheirColumnsBetweenColumnsAndDerive() {
        PipelineSpec plan = loader.loadAll(resource("steps")).get("sensor_sites");

        StepSpec step = plan.silver().steps().getFirst();
        assertThat(step.transformationName()).isEqualTo("site_lookup");
        assertThat(step.transformationVersion()).isEqualTo(2);
        assertThat(step.inputs())
                .containsExactly(
                        Map.entry("readings", "rows"),
                        Map.entry("sensors", "reference.sensors"),
                        Map.entry("sites", "lab_sites.current"));
        assertThat(step.params()).containsEntry("prefix", "site").containsEntry("keep-unknown", true);
        assertThat(step.references()).containsExactly("sensors");
        assertThat(plan.silverReadsReferences()).isTrue();
        assertThat(plan.factSchema().fieldNames())
                .containsExactly(
                        "sensorId",
                        "readAt",
                        "celsius",
                        "siteName",
                        "siteRegion",
                        "fahrenheit",
                        "label",
                        "factUniqueId",
                        "processedAt");
        assertThat(plan.changeColumns()).containsExactly("sensorId", "readAt");
        GoldSpec warm = plan.gold().get(1);
        assertThat(warm.usesTransformation()).isTrue();
        assertThat(warm.asStep().inputs())
                .containsExactly(Map.entry("readings", "rows"), Map.entry("sensors", "reference.sensors"));
        assertThat(warm.references()).containsExactly("sensors");
        assertThat(plan.usesTransformations()).isTrue();
    }

    @Test
    void steps_reportEveryProblem() {
        assertThatThrownBy(() -> loader.loadAll(resource("badsteps")))
                .isInstanceOf(InvalidPipelineSpecException.class)
                .hasMessageContaining("silver.steps.sited.transformation must read <name>@<version>, got 'site_lookup'")
                .hasMessageContaining("silver.steps.sited.inputs.Readings: an input is named like a Java record field")
                .hasMessageContaining("reference 'devices' is not declared under references")
                .hasMessageContaining("source 'lab_sites.everything' must be rows, reference.<name> or")
                .hasMessageContaining("silver.steps.sited.adds.siteName.type: cannot parse 'strng'")
                .hasMessageContaining("column 'celsius' is declared twice")
                .hasMessageContaining("silver.steps.sited.adds._hidden is reserved by the platform")
                .hasMessageContaining("silver.steps.sited.adds._hidden: a step's column comes from its transformation")
                .hasMessageContaining("step 'sited' is declared twice")
                .hasMessageContaining("gold both needs exactly one of sql, transformation and from")
                .hasMessageContaining("gold neither needs exactly one of sql, transformation and from")
                .hasMessageContaining("gold unwired.transformation must read <name>@<version>, got 'warm_sites'")
                .hasMessageContaining("gold unwired reads 'everything', which is not current, a reference, an input or"
                        + " a gold table of this pipeline")
                .hasMessageContaining("gold unwired reads 'devices', which is not current");
    }

    @Test
    void keepClosedDays_isAtLeastOneDay() throws Exception {
        String earthquakes =
                Files.readString(resource("examples").resolve("earthquakes").resolve(PipelineSpecLoader.PLAN_FILE));

        PipelineSpec kept = loader.parse(
                earthquakes.replace("  keys: [eventId]", "  keys: [eventId]\n  keep-closed-days: 365"), "earthquakes");

        assertThat(kept.history().keepClosedDays()).isEqualTo(365);
        assertThatThrownBy(() -> loader.parse(
                        earthquakes.replace("  keys: [eventId]", "  keys: [eventId]\n  keep-closed-days: 0"),
                        "earthquakes"))
                .isInstanceOf(InvalidPipelineSpecException.class)
                .hasMessageContaining("history.keep-closed-days must be at least 1, got 0");
    }

    @Test
    void tree_keepsKeyOrderAndScalarTypes() throws Exception {
        String earthquakes =
                Files.readString(resource("examples").resolve("earthquakes").resolve(PipelineSpecLoader.PLAN_FILE));

        Map<String, Object> tree = loader.tree(earthquakes, "earthquakes");

        assertThat(tree.keySet()).startsWith("dataset", "version", "owner");
        assertThat(tree.get("version")).isEqualTo(3);
        assertThat(tree.get("source"))
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .extractingByKey("options")
                .isEqualTo(Map.of("header", "true"));
        assertThat(loader.tree(" ", "new")).isEmpty();
        assertThatThrownBy(() -> loader.tree("dataset: [", "broken")).isInstanceOf(InvalidPipelineSpecException.class);
    }

    @Test
    void examplePlans_areValid() {
        assertThat(loader.loadAll(resource("examples")).all())
                .extracting(PipelineSpec::dataset)
                .contains("earthquakes", "taxi_trips");
    }

    @Test
    void compute_readsOnlyTheComputeBlock() {
        assertThat(loader.compute("dataset: x\ncompute: { driver-memory: 2g, executors: { max: 3 } }\n", "x.yml"))
                .isEqualTo(new ComputeSpec(new ComputeSpec.Executors(null, 3), "2g", null, null, null, null));
        assertThat(loader.compute("dataset: x\n", "x.yml")).isEqualTo(ComputeSpec.DEFAULT);
        assertThatThrownBy(() -> loader.compute("compute: { memory: 2g }\n", "x.yml"))
                .isInstanceOf(InvalidPipelineSpecException.class)
                .hasMessageContaining("unknown key 'memory'");
    }
}
