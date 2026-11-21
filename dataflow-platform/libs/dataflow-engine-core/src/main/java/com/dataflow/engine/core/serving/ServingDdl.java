package com.dataflow.engine.core.serving;

import com.dataflow.spec.ServeSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.lake.Layer;
import com.dataflow.spec.lake.ObjectStore;

import java.util.List;
import java.util.stream.Collectors;

public final class ServingDdl {

    public static final String EVENTS_VIEW = "run_events";

    private final ServingSettings settings;
    private final String lakeRoot;

    public ServingDdl(ServingSettings settings, String lakeRoot) {
        this.settings = settings;
        this.lakeRoot = lakeRoot;
    }

    private static String refresher(ServeSpec serve) {
        return serve.table() + "_refresh";
    }

    public String createDatabase() {
        return "CREATE DATABASE IF NOT EXISTS " + settings.database();
    }

    public String source(String dataset, ServeSpec serve) {
        if (serve.servesCurrent()) {
            return "SELECT * EXCEPT (%s, %s) FROM %s WHERE %s IS NULL"
                    .formatted(
                            SystemColumns.CONTENT_HASH,
                            SystemColumns.END_AT,
                            lake(Lake.key(Layer.HISTORY, dataset)),
                            SystemColumns.END_AT);
        }
        return "SELECT * FROM " + lake(Lake.key(Layer.GOLD, dataset, serve.goldName()));
    }

    public String view(String dataset, ServeSpec serve) {
        return "CREATE OR REPLACE VIEW %s DEFINER = CURRENT_USER SQL SECURITY DEFINER AS %s"
                .formatted(table(serve.table()), source(dataset, serve));
    }

    public String copyTable(String dataset, ServeSpec serve) {
        return "CREATE TABLE IF NOT EXISTS %s ENGINE = MergeTree ORDER BY %s SETTINGS allow_nullable_key = 1 EMPTY AS %s"
                .formatted(table(serve.table()), serve.orderBy(), source(dataset, serve));
    }

    public String copyRefresher(String dataset, ServeSpec serve) {
        return "CREATE MATERIALIZED VIEW IF NOT EXISTS %s REFRESH EVERY %s TO %s EMPTY AS %s"
                .formatted(
                        table(refresher(serve)), settings.refreshEvery(), table(serve.table()), source(dataset, serve));
    }

    public String describeSource(String dataset, ServeSpec serve) {
        return "DESCRIBE TABLE (%s)".formatted(source(dataset, serve));
    }

    public String describeTable(ServeSpec serve) {
        return "DESCRIBE TABLE " + table(serve.table());
    }

    public List<String> dropCopy(ServeSpec serve) {
        return List.of(
                "DROP VIEW IF EXISTS " + table(refresher(serve)), "DROP TABLE IF EXISTS " + table(serve.table()));
    }

    public List<String> refresh(ServeSpec serve) {
        return List.of("SYSTEM REFRESH VIEW " + table(refresher(serve)), "SYSTEM WAIT VIEW " + table(refresher(serve)));
    }

    public String count(ServeSpec serve) {
        return "SELECT count() FROM " + table(serve.table());
    }

    public String eventsView(List<String> datasets) {
        String union = datasets.stream()
                .map(dataset -> "SELECT * FROM " + lake(Lake.eventsKey(dataset)))
                .collect(Collectors.joining(" UNION ALL "));
        return "CREATE OR REPLACE VIEW %s DEFINER = CURRENT_USER SQL SECURITY DEFINER AS %s"
                .formatted(table(EVENTS_VIEW), union);
    }

    public String table(String name) {
        return settings.database() + "." + name;
    }

    private String lake(String key) {
        if (ObjectStore.of(lakeRoot) == ObjectStore.AZURE) {
            String path =
                    (ObjectStore.prefix(lakeRoot) + key).replace("\\", "\\\\").replace("'", "''");
            return "deltaLakeAzure(%s, blob_path = '%s')".formatted(settings.collection(), path);
        }
        return "deltaLake(%s, filename = '%s')".formatted(settings.collection(), key);
    }
}
