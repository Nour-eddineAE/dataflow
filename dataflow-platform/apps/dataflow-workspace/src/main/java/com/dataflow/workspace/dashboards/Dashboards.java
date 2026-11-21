package com.dataflow.workspace.dashboards;

import com.dataflow.spec.BoardSpec;
import com.dataflow.spec.BoardSpecLoader;
import com.dataflow.spec.InvalidProjectSpecException;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.catalog.Catalog;
import com.dataflow.workspace.catalog.LakeTable;
import com.dataflow.workspace.catalog.TableProfile;
import com.dataflow.workspace.catalog.TableProfiles;
import com.dataflow.workspace.catalog.TableService;
import com.dataflow.workspace.catalog.UnknownTableException;
import com.dataflow.workspace.releases.DeployedView;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.QueryResult;
import com.dataflow.workspace.specs.SpecStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@Service
public class Dashboards {

    private final SpecStore specs;
    private final TableService tables;
    private final TableProfiles profiles;
    private final ClickHouse serving;
    private final DeployedView deployed;
    private final BoardSpecLoader boards = new BoardSpecLoader();
    private final LruCache cache = new LruCache();

    public Dashboards(
            SpecStore specs, TableService tables, TableProfiles profiles, ClickHouse serving, DeployedView deployed) {
        this.specs = specs;
        this.tables = tables;
        this.profiles = profiles;
        this.serving = serving;
        this.deployed = deployed;
    }

    static List<Chart> arrange(String table, List<Chart> charts, BoardSpec overrides) {
        List<Chart> kept = charts.stream()
                .filter(chart -> !overrides.hide().contains(table + "/" + chart.id()))
                .toList();
        List<Chart> arranged = new ArrayList<>();
        for (String wanted : overrides.first()) {
            kept.stream()
                    .filter(chart -> wanted.equals(table + "/" + chart.id()))
                    .findFirst()
                    .ifPresent(arranged::add);
        }
        kept.stream().filter(chart -> !arranged.contains(chart)).forEach(arranged::add);
        return arranged;
    }

    public Dashboard dashboard(String dataset) {
        PipelineSpec spec = specs.spec(dataset);
        List<String> problems = new ArrayList<>();
        BoardSpec overrides = overrides(dataset, problems);
        List<TableBoard> boards = new ArrayList<>();
        for (LakeTable table : tables.tables(dataset)) {
            boolean gold = Catalog.GOLD.equals(table.layer());
            if (!table.exists() || !(gold || Catalog.CURRENT.equals(table.id()))) {
                continue;
            }
            TableProfile profile = profile(dataset, table);
            ChartPlanner.Plan plan = ChartPlanner.plan(spec, gold, profile);
            boards.add(new TableBoard(
                    table.id(),
                    table.id(),
                    table.sqlName(),
                    profile.rows(),
                    plan.time(),
                    plan.latest(),
                    plan.filters(),
                    arrange(table.id(), plan.charts(), overrides)));
        }
        return new Dashboard(dataset, boards, problems);
    }

    private BoardSpec overrides(String dataset, List<String> problems) {
        String file = BoardSpec.file(dataset);
        try {
            return deployed.releasedFile(dataset, file)
                    .map(content -> boards.parse(content, file))
                    .orElse(BoardSpec.NONE);
        } catch (InvalidProjectSpecException e) {
            problems.addAll(
                    e.problems().stream().map(problem -> file + ": " + problem).toList());
            return BoardSpec.NONE;
        }
    }

    public ChartData chart(String dataset, String tableId, String chartId, String range, Map<String, String> filters) {
        LakeTable table = tables.written(dataset, tableId);
        TableBoard board = dashboard(dataset).tables().stream()
                .filter(candidate -> candidate.table().equals(tableId))
                .findFirst()
                .orElseThrow(() -> new UnknownTableException(dataset, tableId));
        Chart chart = board.charts().stream()
                .filter(candidate -> candidate.id().equals(chartId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(dataset + "." + tableId + " has no chart " + chartId));
        Map<String, String> applied = new TreeMap<>(filters);
        applied.keySet().forEach(column -> {
            if (!board.filters().contains(column)) {
                throw new IllegalArgumentException(column + " is not a filter of " + dataset + "." + tableId);
            }
        });
        String key = String.join(
                "|",
                "chart",
                dataset,
                tableId,
                String.valueOf(table.commits()),
                chartId,
                String.valueOf(range),
                applied.toString());
        Object cached = cache.get(key);
        if (cached instanceof ChartData data) {
            return data.fromCache();
        }
        String timeType = type(dataset, table, board.time());
        ChartQueries.Window window = new ChartQueries.Window(board.time(), timeType, board.latest(), range, applied);
        ChartQueries.Built built =
                ChartQueries.build(chart, tables.source(dataset, tableId).asTable("source"), window);
        ChartQueries.Built shown =
                ChartQueries.build(chart, table(name(table.sqlName().split("\\."))), window);
        QueryResult result = serving.fetch(built.query(), serving.dashboardRows());
        ChartData data = new ChartData(
                chartId,
                serving.render(shown.query()),
                built.grain(),
                result.rows(),
                result.truncated(),
                result.elapsedMs(),
                false);
        cache.put(key, data);
        return data;
    }

    private TableProfile profile(String dataset, LakeTable table) {
        String key = String.join("|", "profile", dataset, table.id(), String.valueOf(table.commits()));
        Object cached = cache.get(key);
        if (cached instanceof TableProfile profile) {
            return profile;
        }
        TableProfile profile = profiles.profile(tables.source(dataset, table.id()));
        cache.put(key, profile);
        return profile;
    }

    private String type(String dataset, LakeTable table, String column) {
        if (column == null) {
            return null;
        }
        return profile(dataset, table).columns().stream()
                .filter(candidate -> candidate.name().equals(column))
                .map(candidate -> candidate.type())
                .findFirst()
                .orElse(null);
    }
}
