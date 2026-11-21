package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Visibility;
import com.dataflow.workspace.catalog.TableService;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.ColumnUsage;
import com.dataflow.workspace.serving.SystemTables;
import com.dataflow.workspace.serving.TableUsage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class CatalogUsageController {

    private static final int MAX_USAGE_DAYS = 365;

    private final TableService tables;
    private final Visibility visibility;
    private final ClickHouse serving;
    private final SystemTables system;
    private final Clock clock;

    public CatalogUsageController(
            TableService tables, Visibility visibility, ClickHouse serving, SystemTables system, Clock clock) {
        this.tables = tables;
        this.visibility = visibility;
        this.serving = serving;
        this.system = system;
        this.clock = clock;
    }

    @GetMapping("/catalog/usage")
    public List<TableUsage> usage(@RequestParam(defaultValue = "30") int days) {
        Set<String> visible = readable();
        return system.usage(since(days)).stream()
                .filter(usage -> visible.contains(usage.table()))
                .toList();
    }

    @GetMapping("/catalog/usage/columns")
    public List<ColumnUsage> columnUsage(@RequestParam String table, @RequestParam(defaultValue = "30") int days) {
        if (!readable().contains(table)) {
            return List.of();
        }
        return system.columnUsage(table, since(days));
    }

    private Set<String> readable() {
        return visibility.sqlNames(tables.tree(), serving.database());
    }

    private LocalDate since(int days) {
        return LocalDate.now(clock).minusDays(Limits.clamp(days, MAX_USAGE_DAYS));
    }
}
