package com.dataflow.spec;

import com.dataflow.spec.GoldStepSpec.Operation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DerivedPipelineTest {

    private static final String HEADER = """
            dataset: draft
            version: 3
            """;

    private final PipelineSpecLoader loader = new PipelineSpecLoader();

    private static String fixture() throws IOException {
        return Files.readString(PipelineSpecLoaderTest.resource("derived")
                .resolve("zone_hours")
                .resolve(PipelineSpecLoader.PLAN_FILE));
    }

    private PipelineSpec zoneHours() {
        return loader.loadAll(PipelineSpecLoaderTest.resource("derived")).get("zone_hours");
    }

    private void assertProblems(String yaml, String... problems) {
        assertThatThrownBy(() -> loader.parse(HEADER + yaml, "draft"))
                .isInstanceOfSatisfying(
                        InvalidPipelineSpecException.class,
                        e -> assertThat(String.join("\n", e.problems())).contains(problems));
    }

    @Test
    void aPipelineWithoutSourceReadsOtherDatasetsTables() {
        PipelineSpec plan = zoneHours();

        assertThat(plan.ingests()).isFalse();
        assertThat(plan.inputs()).containsKeys("trips", "zones", "quakes");
        assertThat(plan.tables().input("zones"))
                .hasValueSatisfying(table -> assertThat(table.toString()).isEqualTo("taxi_trips.reference.taxi_zones"));
        assertThat(plan.materialized()).extracting(GoldSpec::name).containsExactly("borough_hours", "busiest",
                "trip_days");
    }

    @Test
    void goldStepsReadAsOneOperationEach() {
        GoldSpec zoned = zoneHours().gold().getFirst();

        assertThat(zoned.body()).isEqualTo(GoldSpec.Body.STEPS);
        assertThat(zoned.materialize()).isFalse();
        assertThat(zoned.steps()).extracting(GoldStepSpec::operation).containsExactly(Operation.JOIN, Operation.DERIVE);
        JoinSpec join = zoned.steps().getFirst().join();
        assertThat(join.type()).isEqualTo(JoinSpec.Type.LEFT);
        assertThat(join.on()).containsEntry("pickupLocationId", "LocationID");
        assertThat(zoned.steps().get(1).derive()).containsKeys("hour", "day");
    }

    @Test
    void theGraphOrdersTablesAndSeesThroughUnstoredOnes() {
        TableGraph graph = zoneHours().tables();

        assertThat(graph.ordered()).extracting(GoldSpec::name)
                .containsExactly("zoned_trips", "borough_hours", "busiest", "trip_days");
        assertThat(graph.reads("busiest")).containsExactlyInAnyOrder("borough_hours", "quakes");
        assertThat(graph.sources("borough_hours")).containsExactlyInAnyOrder("trips", "zones");
        assertThat(graph.kind("quakes")).contains(TableGraph.Kind.INPUT);
        assertThat(graph.readers("zoned_trips")).containsExactly("borough_hours", "trip_days");
        assertThat(graph.cycle()).isEmpty();
    }

    @Test
    void sqlRelationsSkipCommonTableExpressionsAndFindSubqueries() {
        assertThat(SqlRelations.of("""
                WITH totals AS (SELECT day, count(*) AS n FROM Current GROUP BY day)
                SELECT * FROM totals t JOIN zones z ON t.day = z.day
                WHERE t.day IN (SELECT day FROM holidays)
                """)).containsExactlyInAnyOrder("current", "zones", "holidays");
    }

    @Test
    void anIngestionPipelineMayJoinAnotherDatasetThroughInputs() throws IOException {
        String earthquakes = Files.readString(PipelineSpecLoaderTest.resource("examples")
                .resolve("earthquakes")
                .resolve(PipelineSpecLoader.PLAN_FILE));

        PipelineSpec plan = loader.parse(earthquakes + """

                inputs:
                  trips: taxi_trips.current
                """, "earthquakes");

        assertThat(plan.ingests()).isTrue();
        assertThat(plan.tables().kind("trips")).contains(TableGraph.Kind.INPUT);
        assertThat(plan.tables().kind("current")).contains(TableGraph.Kind.CURRENT);
    }

    @Test
    void sqlThatNamesAnotherDatasetDirectlyIsToldToUseInputs() {
        assertProblems("""
                gold:
                  - name: joined
                    sql: SELECT * FROM taxi_trips.current
                """,
                "a pipeline without source needs inputs (other datasets' tables) or references to read",
                "gold joined reads 'taxi_trips.current', which is not a reference, an input or a gold table of this"
                        + " pipeline; name it under inputs, for example inputs: { taxi_trips_current:"
                        + " taxi_trips.current }");
    }

    @Test
    void aDerivedPipelineHasNoSilverNorCurrent() {
        assertProblems("""
                inputs: { trips: taxi_trips.current }
                silver:
                  columns: [{ name: id, type: string, from: id }]
                gold:
                  - { name: copy, sql: SELECT * FROM current }
                serve:
                  - { from: current, table: copy }
                """,
                "bronze, silver and history need a source",
                "gold copy reads 'current', which is not a reference",
                "serve.from 'current' must be one of [gold.copy]");
    }

    @Test
    void tablesThatReadEachOtherInALoopAreRefused() {
        assertProblems("""
                inputs: { trips: taxi_trips.current }
                gold:
                  - { name: first, sql: SELECT * FROM second }
                  - { name: second, from: first, steps: [{ filter: "x > 1" }] }
                """,
                "gold tables read each other in a loop: first -> second -> first");
    }

    @Test
    void everyStepIsChecked() {
        assertProblems("""
                inputs: { trips: taxi_trips.current, zones: taxi_trips.reference.taxi_zones }
                gold:
                  - name: broken
                    from: trips
                    materialize: false
                    steps:
                      - { filter: "a >", drop: [b] }
                      - join: { with: zones, on: { a: b }, condition: "left.a = right.b" }
                      - join: { with: zones, type: anti, on: { a: b }, prefix: zone }
                      - aggregate: { by: [a] }
                      - derive: { computedAt: "now()" }
                      - dedupe: { keys: [] }
                      - union: [nowhere]
                serve:
                  - { from: gold.broken, table: broken }
                """,
                "gold broken step 1 needs exactly one of [join, union, filter, derive, select, rename, drop,"
                        + " aggregate, dedupe], found [filter, drop]",
                "gold broken step 2 (join) needs exactly one of on (matching columns) and condition",
                "gold broken step 3 (join): anti joins keep no right columns, so no prefix",
                "gold broken step 4 (aggregate) needs at least one measure",
                "gold broken step 5 (derive) column computedAt is reserved by the platform",
                "gold broken step 6 (dedupe) needs at least one key",
                "gold broken reads 'nowhere', which is not",
                "serve.from 'gold.broken' must be one of []");
    }

    @Test
    void namesAreSharedAcrossReferencesInputsAndTables() throws IOException {
        assertProblems(fixture().replace("dataset: zone_hours\nversion: 3\n", "").replace("name: busiest", "name: trips"),
                "name 'trips' is declared twice (current, references, inputs and gold tables share one set of names)");
    }

    @Test
    void linksAreCheckedAcrossThePipelinesOfAProject() {
        Map<String, PipelineSpec> plans = new TreeMap<>();
        loader.loadAll(PipelineSpecLoaderTest.resource("examples")).all().forEach(plan -> plans.put(plan.dataset(), plan));
        PipelineSpec zoneHours = zoneHours();
        plans.put(zoneHours.dataset(), zoneHours);

        assertThat(PipelineLinks.problems(new PipelineSpecs(plans))).isEmpty();
        assertThat(PipelineLinks.upstream(new PipelineSpecs(plans)).get("zone_hours"))
                .containsExactlyInAnyOrder("taxi_trips", "earthquakes");

        plans.put("first", loader.parse(HEADER.replace("draft", "first") + """
                inputs: { other: second.gold.out, quakes: earthquakes.gold.nothing, rows: zone_hours.silver }
                gold: [{ name: out, sql: SELECT * FROM other }]
                """, "first"));
        plans.put("second", loader.parse(HEADER.replace("draft", "second") + """
                inputs: { other: first.gold.out }
                gold: [{ name: out, sql: SELECT * FROM other }]
                """, "second"));

        assertThat(PipelineLinks.problems(new PipelineSpecs(plans))).containsExactly(
                "first input quakes reads earthquakes.gold.nothing, but earthquakes stores no gold table nothing",
                "first input rows reads zone_hours.silver, but zone_hours has no source, so no silver",
                "pipelines read each other in a loop: first -> second -> first");
    }
}
