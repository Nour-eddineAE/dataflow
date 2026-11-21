package com.dataflow.workspace.serving;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.config.WorkspaceProperties;
import org.junit.jupiter.api.Test;

import static com.dataflow.workspace.serving.RecordingClickHouse.render;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.selectFrom;

class LakeFunctionsTest {

    private static LakeFunctions functions(String root) {
        WorkspaceProperties base = Workspace.properties();
        WorkspaceProperties.Serving serving = base.serving();
        return new LakeFunctions(new WorkspaceProperties(
                base.person(),
                base.auth(),
                base.git(),
                base.releases(),
                new WorkspaceProperties.Serving(
                        serving.url(),
                        serving.user(),
                        serving.password(),
                        serving.database(),
                        serving.collection(),
                        root,
                        serving.maxRows(),
                        serving.dashboardRows(),
                        serving.timeout()),
                base.tower(),
                base.catalog(),
                base.links(),
                base.alerts(),
                base.dashboards()));
    }

    @Test
    void azureUsesBlobPathsForTablesListingsAndTaskResults() {
        LakeFunctions lake = functions("abfss://lake@acme.dfs.core.windows.net/prod");

        assertThat(render(selectFrom(lake.delta("history/earthquakes/"))))
                .isEqualTo("select * from deltaLakeAzure(lake, blob_path = 'prod/history/earthquakes/')");
        assertThat(render(selectFrom(lake.objects("**/_delta_log/*.json"))))
                .isEqualTo("select * from azureBlobStorage(lake, blob_path = 'prod/**/_delta_log/*.json', format ="
                        + " 'One')");
        assertThat(render(selectFrom(lake.blob("system/tasks/task.json"))))
                .contains("blob_path = 'prod/system/tasks/task.json', format = 'RawBLOB'");
        assertThat(render(selectFrom(lake.jsonDocuments("history/earthquakes/_delta_log/*.json"))))
                .contains("blob_path = 'prod/history/earthquakes/_delta_log/*.json', format = 'JSONAsString'");
    }

    @Test
    void googleAndS3UseTheNamedCollectionRoot() {
        for (String root : new String[] {"s3a://lake/prod", "gs://lake/prod"}) {
            LakeFunctions lake = functions(root);
            assertThat(render(selectFrom(lake.delta("history/earthquakes/"))))
                    .isEqualTo("select * from deltaLake(lake, filename = 'history/earthquakes/')");
            assertThat(render(selectFrom(lake.blob("system/tasks/task.json"))))
                    .isEqualTo("select * from s3(lake, filename = 'system/tasks/task.json', format = 'RawBLOB')");
        }
    }
}
