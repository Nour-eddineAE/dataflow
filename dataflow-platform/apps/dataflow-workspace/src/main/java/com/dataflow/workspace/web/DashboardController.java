package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.access.Visibility;
import com.dataflow.workspace.dashboards.ChartData;
import com.dataflow.workspace.dashboards.Dashboard;
import com.dataflow.workspace.dashboards.Dashboards;
import com.dataflow.workspace.dashboards.Overview;
import com.dataflow.workspace.dashboards.PipelineOverviews;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/boards")
public class DashboardController {

    private static final String FILTER = "filter.";

    private final Dashboards dashboards;
    private final PipelineOverviews overviews;
    private final Access access;
    private final Visibility visibility;

    public DashboardController(
            Dashboards dashboards, PipelineOverviews overviews, Access access, Visibility visibility) {
        this.dashboards = dashboards;
        this.overviews = overviews;
        this.access = access;
        this.visibility = visibility;
    }

    @GetMapping("/{dataset}")
    public Dashboard dashboard(@PathVariable String dataset) {
        visibility.requireDataset(dataset);
        Caller caller = access.caller();
        Dashboard board = dashboards.dashboard(dataset);
        return new Dashboard(
                board.dataset(),
                board.tables().stream()
                        .filter(table -> access.can(caller, Privilege.SELECT, Securable.table(dataset, table.table())))
                        .toList(),
                board.problems());
    }

    @GetMapping("/{dataset}/overview")
    public Overview overview(@PathVariable String dataset) {
        visibility.requireDataset(dataset);
        return overviews.overview(dataset);
    }

    @GetMapping("/{dataset}/{table}/{chart}")
    public ChartData chart(
            @PathVariable String dataset,
            @PathVariable String table,
            @PathVariable String chart,
            @RequestParam(defaultValue = "all") String range,
            @RequestParam Map<String, String> params) {
        Map<String, String> filters = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith(FILTER)) {
                filters.put(key.substring(FILTER.length()), value);
            }
        });
        visibility.requireTable(dataset, table);
        return dashboards.chart(dataset, table, chart, range, filters);
    }
}
