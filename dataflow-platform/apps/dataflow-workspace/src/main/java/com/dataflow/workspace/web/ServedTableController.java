package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Visibility;
import com.dataflow.workspace.catalog.TableProfile;
import com.dataflow.workspace.catalog.TableProfiles;
import com.dataflow.workspace.catalog.TableService;
import com.dataflow.workspace.serving.QueryResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/serving/{table}")
public class ServedTableController {

    private final TableService tables;
    private final TableProfiles profiles;
    private final Visibility visibility;

    public ServedTableController(TableService tables, TableProfiles profiles, Visibility visibility) {
        this.tables = tables;
        this.profiles = profiles;
        this.visibility = visibility;
    }

    @GetMapping("/sample")
    public QueryResult sample(@PathVariable String table, @RequestParam(defaultValue = "100") int limit) {
        requireServed(table);
        return tables.servedSample(table, Limits.clamp(limit, Limits.MAX_SAMPLE));
    }

    @GetMapping("/profile")
    public TableProfile profile(@PathVariable String table) {
        requireServed(table);
        return profiles.profile(tables.servedSource(table));
    }

    @GetMapping("/columns")
    public QueryResult columns(@PathVariable String table) {
        requireServed(table);
        return tables.servedColumns(table);
    }

    private void requireServed(String table) {
        tables.served().stream()
                .filter(served -> served.table().equals(table))
                .findFirst()
                .ifPresent(visibility::requireServed);
    }
}
