package com.dataflow.workspace.catalog;

import com.dataflow.spec.ColumnSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class ColumnRoles {

    private static final Set<String> SILVER_SHAPED =
            Set.of(Catalog.SILVER, Catalog.QUARANTINE, Catalog.HISTORY, Catalog.CURRENT);

    private ColumnRoles() {}

    static Map<String, ColumnInfo> of(PipelineSpec spec, String tableId) {
        Map<String, ColumnInfo> roles = new HashMap<>();
        if (SILVER_SHAPED.contains(tableId)) {
            declared(roles, spec);
        }
        systemColumns(roles, spec);
        if (tableId.equals(Catalog.BRONZE)) {
            roles.keySet().removeIf(name -> !name.startsWith(SystemColumns.PREFIX));
        }
        return roles;
    }

    private static void declared(Map<String, ColumnInfo> roles, PipelineSpec spec) {
        for (ColumnSpec column : spec.silver().columns()) {
            String required = column.required() ? "required; " : "";
            roles.put(
                    column.name(),
                    new ColumnInfo(
                            column.name(),
                            column.type(),
                            "declared",
                            column.from(),
                            required
                                    + (spec.changeColumns().contains(column.name())
                                            ? "a change here makes a new version"
                                            : "never makes a new version")));
        }
        for (ColumnSpec column : spec.silver().derive()) {
            roles.put(
                    column.name(),
                    new ColumnInfo(
                            column.name(),
                            column.type(),
                            "derived",
                            column.from(),
                            "computed after the declared columns"));
        }
    }

    private static void systemColumns(Map<String, ColumnInfo> roles, PipelineSpec spec) {
        system(roles, SystemColumns.RESCUED_DATA, "Values that did not fit the bronze schema");
        system(roles, SystemColumns.SOURCE_FILE, "Landing file the row came from");
        system(roles, SystemColumns.INGESTED_AT, "When bronze read the file");
        system(roles, SystemColumns.RUN_ID, "Run that wrote the row");
        system(
                roles,
                SystemColumns.FACT_UNIQUE_ID,
                "SHA-256 of the history keys " + spec.history().keys());
        system(roles, SystemColumns.PROCESSED_AT, "When silver refined the row");
        system(roles, SystemColumns.CONTENT_HASH, "SHA-256 of the change columns; decides whether a fact changed");
        system(roles, SystemColumns.FAILED_EXPECTATIONS, "Drop rules this row broke");
        system(roles, SystemColumns.COMPUTED_AT, "When the gold table was rebuilt");
        system(roles, SystemColumns.START_AT, "When this version became current");
        system(roles, SystemColumns.END_AT, "When a newer version replaced it; NULL while current");
    }

    private static void system(Map<String, ColumnInfo> roles, String name, String description) {
        roles.put(name, new ColumnInfo(name, null, "system", null, description));
    }
}
