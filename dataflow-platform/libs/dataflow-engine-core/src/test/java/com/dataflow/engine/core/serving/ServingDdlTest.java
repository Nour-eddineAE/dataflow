package com.dataflow.engine.core.serving;

import com.dataflow.spec.ServeSpec;
import com.dataflow.spec.ServeSpec.Mode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServingDdlTest {

    private final ServingDdl ddl = new ServingDdl(new ServingSettings("serving", "lake", "1 DAY"), "s3a://lake");

    @Test
    void view_readsTheCurrentRowsOfTheHistoryInPlace() {
        assertThat(ddl.view("earthquakes", new ServeSpec("current", "earthquakes", Mode.VIEW, null)))
                .isEqualTo("CREATE OR REPLACE VIEW serving.earthquakes DEFINER = CURRENT_USER SQL SECURITY DEFINER AS"
                        + " SELECT * EXCEPT (contentHash, __END_AT) FROM deltaLake(lake, filename ="
                        + " 'history/earthquakes/') WHERE __END_AT IS NULL");
    }

    @Test
    void copy_isAMergeTreeRefreshedByAMaterializedView() {
        ServeSpec serve = new ServeSpec("gold.regions", "earthquakes_regions", Mode.COPY, "region");

        assertThat(ddl.copyTable("earthquakes", serve))
                .isEqualTo("CREATE TABLE IF NOT EXISTS serving.earthquakes_regions ENGINE = MergeTree ORDER BY region "
                        + "SETTINGS allow_nullable_key = 1 EMPTY AS SELECT * FROM "
                        + "deltaLake(lake, filename = 'gold/earthquakes/regions/')");
        assertThat(ddl.copyRefresher("earthquakes", serve))
                .isEqualTo("CREATE MATERIALIZED VIEW IF NOT EXISTS serving.earthquakes_regions_refresh REFRESH EVERY 1"
                        + " DAY TO serving.earthquakes_regions EMPTY AS SELECT * FROM deltaLake(lake, filename ="
                        + " 'gold/earthquakes/regions/')");
        assertThat(ddl.refresh(serve))
                .containsExactly(
                        "SYSTEM REFRESH VIEW serving.earthquakes_regions_refresh",
                        "SYSTEM WAIT VIEW serving.earthquakes_regions_refresh");
    }

    @Test
    void eventsView_unionsEveryDatasetsEventLog() {
        assertThat(ddl.eventsView(List.of("a", "b")))
                .isEqualTo("CREATE OR REPLACE VIEW serving.run_events DEFINER = CURRENT_USER SQL SECURITY DEFINER AS "
                        + "SELECT * FROM deltaLake(lake, filename = 'system/events/a/') UNION ALL "
                        + "SELECT * FROM deltaLake(lake, filename = 'system/events/b/')");
    }

    @Test
    void azure_readsTablesAndEventsUnderTheLakePrefix() {
        ServingDdl azure = new ServingDdl(
                new ServingSettings("serving", "lake", "1 DAY"), "abfss://lake@acme.dfs.core.windows.net/prod/");

        assertThat(azure.source("earthquakes", new ServeSpec("current", "earthquakes", Mode.VIEW, null)))
                .contains("deltaLakeAzure(lake, blob_path = 'prod/history/earthquakes/')");
        assertThat(azure.eventsView(List.of("earthquakes")))
                .contains("deltaLakeAzure(lake, blob_path = 'prod/system/events/earthquakes/')");
        assertThat(azure.copyRefresher("earthquakes", new ServeSpec("gold.regions", "regions", Mode.COPY, "region")))
                .contains("deltaLakeAzure(lake, blob_path = 'prod/gold/earthquakes/regions/')");
    }

    @Test
    void google_usesTheNamedCollectionsS3Endpoint() {
        ServingDdl google = new ServingDdl(new ServingSettings("serving", "lake", "1 DAY"), "gs://lake/prod");

        assertThat(google.source("earthquakes", new ServeSpec("current", "earthquakes", Mode.VIEW, null)))
                .contains("deltaLake(lake, filename = 'history/earthquakes/')");
    }
}
