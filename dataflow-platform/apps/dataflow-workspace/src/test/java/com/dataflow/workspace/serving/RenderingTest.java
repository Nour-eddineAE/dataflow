package com.dataflow.workspace.serving;

import com.dataflow.workspace.Drafts;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.catalog.Catalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dataflow.workspace.serving.ClickHouseDsl.column;
import static com.dataflow.workspace.serving.RecordingClickHouse.render;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.selectFrom;

class RenderingTest {

    private final LakeFunctions lake = new LakeFunctions(Workspace.properties());

    @Test
    void source_ofCurrentDropsTheHistoryColumnsAndClosedVersions() {
        String current = render(Catalog.source(
                lake, Catalog.table(Drafts.spec("earthquakes"), Catalog.CURRENT).orElseThrow()));

        assertThat(current)
                .isEqualTo("select * except (\"contentHash\", \"__END_AT\") "
                        + "from deltaLake(lake, filename = 'history/earthquakes/') where \"__END_AT\" is null");
    }

    @Test
    void render_escapesQuotesAndBackslashesInValues() {
        String statement = render(selectFrom(lake.delta("silver/earthquakes/"))
                .where(column("eventId", String.class).eq("x' OR 1=1 --\\")));

        assertThat(statement).endsWith("where \"eventId\" = 'x'' OR 1=1 --\\\\'");
    }

    @Test
    void render_quotesIdentifiers() {
        String statement = render(selectFrom(lake.delta("silver/earthquakes/"))
                .where(column("odd\"name", String.class).isNotNull()));

        assertThat(statement).endsWith("where \"odd\"\"name\" is not null");
    }

    @Test
    void objects_listsFilesInTheLakeCollection() {
        String statement = render(selectFrom(lake.objects("landing/earthquakes/*")));

        assertThat(statement).isEqualTo("select * from s3(lake, filename = 'landing/earthquakes/*', format = 'One')");
    }

    @Test
    void functions_listsPublicFunctionsWithTheirKind() {
        RecordingClickHouse serving = new RecordingClickHouse()
                .answer(
                        sql -> sql.contains("system"),
                        List.of("name", "aggregate"),
                        List.of(List.of("uniq", true), List.of("toDate", false)));

        assertThat(new SystemTables(serving).functions())
                .containsExactly(new SqlFunction("uniq", true), new SqlFunction("toDate", false));
        assertThat(serving.statements())
                .singleElement()
                .asString()
                .isEqualTo("select \"name\", toBool(\"is_aggregate\") \"aggregate\" from \"system\".\"functions\" "
                        + "where \"name\" not like '\\\\_%' order by \"name\"");
    }

    @Test
    void function_readsTheDocsOfOneFunction() {
        RecordingClickHouse serving = new RecordingClickHouse()
                .answer(
                        sql -> sql.contains("system"),
                        List.of("name", "syntax", "description", "returns"),
                        List.of(List.of("uniq", "uniq(x)", "Counts.", "UInt64")));

        assertThat(new SystemTables(serving).functionDoc("uniq"))
                .contains(new FunctionDoc("uniq", "uniq(x)", "Counts.", "UInt64"));
        assertThat(serving.statements()).singleElement().asString().endsWith("where \"name\" = 'uniq' limit 1");
    }
}
