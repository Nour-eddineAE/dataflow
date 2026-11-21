package com.dataflow.workspace.catalog;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.SystemTables;
import com.dataflow.workspace.specs.SpecStore;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Name;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jooq.impl.DSL.createDatabaseIfNotExists;
import static org.jooq.impl.DSL.dropViewIfExists;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.query;

@Slf4j
@Component
public class CatalogViews {

    private final SpecStore specs;
    private final LakeIndex index;
    private final ClickHouse serving;
    private final LakeFunctions lake;
    private final SystemTables system;
    private Map<String, Long> synced = Map.of();

    public CatalogViews(SpecStore specs, LakeIndex index, ClickHouse serving, LakeFunctions lake, SystemTables system) {
        this.specs = specs;
        this.index = index;
        this.serving = serving;
        this.lake = lake;
        this.system = system;
    }

    private static Name qualified(String sqlName) {
        int dot = sqlName.indexOf('.');
        return name(sqlName.substring(0, dot), sqlName.substring(dot + 1));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        trySync();
    }

    @Scheduled(
            fixedDelayString = WorkspaceProperties.SYNC_EVERY,
            initialDelayString = WorkspaceProperties.SYNC_EVERY)
    public void trySync() {
        try {
            sync(false);
        } catch (RuntimeException e) {
            log.warn("Catalog views not synced: {}", e.getMessage());
        }
    }

    public synchronized int sync(boolean force) {
        Map<String, TableLog> logs = index.refresh();
        Map<String, Long> wanted = new HashMap<>();
        Set<String> databases = new HashSet<>();
        Set<String> declared = new HashSet<>();
        int created = 0;
        for (PipelineSpec spec : specs.valid()) {
            databases.add(Catalog.database(spec.dataset()));
            for (LakeTable table : index.locate(Catalog.tables(spec))) {
                declared.add(table.sqlName());
                if (!table.delta() || !table.exists()) {
                    continue;
                }
                wanted.put(table.sqlName(), table.commits());
                if (!force && table.commits() == synced.getOrDefault(table.sqlName(), -1L)) {
                    continue;
                }
                serving.execute(createDatabaseIfNotExists(name(Catalog.database(spec.dataset()))));
                serving.execute(query(
                        "CREATE OR REPLACE VIEW {0} DEFINER = CURRENT_USER SQL SECURITY DEFINER AS {1}",
                        qualified(table.sqlName()), Catalog.source(lake, table)));
                created++;
            }
        }
        synced = Map.copyOf(wanted);
        dropUndeclared(databases, declared);
        if (created > 0) {
            log.info(
                    "Catalog views synced: {} of {} lake tables ({} delta logs seen)",
                    created,
                    wanted.size(),
                    logs.size());
        }
        return created;
    }

    private void dropUndeclared(Set<String> databases, Set<String> declared) {
        if (databases.isEmpty()) {
            return;
        }
        List<String> undeclared = system.views(databases).stream()
                .filter(view -> !declared.contains(view))
                .sorted()
                .toList();
        undeclared.forEach(view -> serving.execute(dropViewIfExists(qualified(view))));
        if (!undeclared.isEmpty()) {
            log.info("Catalog views dropped, their spec no longer declares them: {}", undeclared);
        }
    }
}
