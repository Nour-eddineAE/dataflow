package com.dataflow.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftBoundariesTest {

    private static final String BASE = """
            dataset: draft
            version: 3
            source: { url: "https://x.test/a.csv", format: csv }
            silver:
              columns:
                - { name: id, type: string, required: true, from: %s }
            history: { keys: [id] }
            """;

    private final PipelineSpecLoader loader = new PipelineSpecLoader();

    private String spec(String column) {
        return BASE.formatted(column);
    }

    private void assertRefused(String yaml, String problem) {
        assertThatThrownBy(() -> loader.parse(yaml, "draft"))
                .isInstanceOfSatisfying(
                        InvalidPipelineSpecException.class,
                        e -> assertThat(String.join("\n", e.problems())).contains(problem));
    }

    @Test
    void anExpressionMayNotReadAnotherTable() {
        assertRefused(spec("\"(SELECT max(secret) FROM delta.`s3a://lake/silver/taxi_trips`)\""), "may not read other tables");
        assertRefused(spec("\"id IN (SELECT id FROM parquet.`/data/x`)\""), "may not read other tables");
    }

    @Test
    void anExpressionOverColumnsIsFine() {
        assertThatCode(() -> loader.parse(spec("\"upper(id)\""), "draft")).doesNotThrowAnyException();
    }

    @Test
    void goldSqlMayNotReadAPathOrAFileFunction() {
        String gold = spec("id") + "gold:\n  - name: stolen\n    sql: \"SELECT * FROM %s\"\n";

        assertRefused(gold.formatted("delta.`s3a://lake/silver/taxi_trips`"), "stolen");
        assertRefused(gold.formatted("table_changes_by_path('s3a://lake/history/taxi_trips', 0)"), "table_changes_by_path");
    }

    @Test
    void goldSqlMayGenerateRows() {
        assertThatCode(() -> loader.parse(
                        spec("id") + "gold:\n  - name: numbers\n    sql: \"SELECT * FROM range(3)\"\n", "draft"))
                .doesNotThrowAnyException();
    }

    @Test
    void orderByIsAListOfColumns() {
        String serve = spec("id") + "gold:\n  - { name: g, sql: SELECT id FROM current }\n"
                + "serve:\n  - { from: gold.g, table: t, mode: copy, order-by: \"%s\" }\n";

        assertThatCode(() -> loader.parse(serve.formatted("(day, region)"), "draft")).doesNotThrowAnyException();
        assertThatCode(() -> loader.parse(serve.formatted("id"), "draft")).doesNotThrowAnyException();
        assertRefused(serve.formatted("id) AS SELECT * FROM serving.payroll --"), "order-by is a list of columns");
    }
}
