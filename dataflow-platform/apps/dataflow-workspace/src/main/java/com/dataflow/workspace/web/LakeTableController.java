package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Visibility;
import com.dataflow.workspace.catalog.Catalog;
import com.dataflow.workspace.catalog.Commit;
import com.dataflow.workspace.catalog.DeltaDetails;
import com.dataflow.workspace.catalog.DeltaMetadata;
import com.dataflow.workspace.catalog.Quarantine;
import com.dataflow.workspace.catalog.Rescued;
import com.dataflow.workspace.catalog.RuleCount;
import com.dataflow.workspace.catalog.TableChanges;
import com.dataflow.workspace.catalog.TableDetail;
import com.dataflow.workspace.catalog.TableProfile;
import com.dataflow.workspace.catalog.TableProfiles;
import com.dataflow.workspace.catalog.TableService;
import com.dataflow.workspace.serving.QueryResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/{dataset}")
public class LakeTableController {

    private final TableService tables;
    private final TableProfiles profiles;
    private final DeltaMetadata metadata;
    private final Quarantine quarantine;
    private final TableChanges changes;
    private final Rescued rescued;
    private final Visibility visibility;

    public LakeTableController(
            TableService tables,
            TableProfiles profiles,
            DeltaMetadata metadata,
            Quarantine quarantine,
            TableChanges changes,
            Rescued rescued,
            Visibility visibility) {
        this.tables = tables;
        this.profiles = profiles;
        this.metadata = metadata;
        this.quarantine = quarantine;
        this.changes = changes;
        this.rescued = rescued;
        this.visibility = visibility;
    }

    @GetMapping("/{table}")
    public TableDetail table(@PathVariable String dataset, @PathVariable String table) {
        visibility.requireTable(dataset, table);
        return tables.detail(dataset, table);
    }

    @GetMapping("/{table}/sample")
    public QueryResult sample(
            @PathVariable String dataset, @PathVariable String table, @RequestParam(defaultValue = "100") int limit) {
        visibility.requireTable(dataset, table);
        return tables.sample(dataset, table, Limits.clamp(limit, Limits.MAX_SAMPLE));
    }

    @GetMapping("/{table}/profile")
    public TableProfile profile(@PathVariable String dataset, @PathVariable String table) {
        visibility.requireTable(dataset, table);
        return profiles.profile(tables.source(dataset, table));
    }

    @GetMapping("/{table}/details")
    public DeltaDetails details(@PathVariable String dataset, @PathVariable String table) {
        visibility.requireTable(dataset, table);
        return metadata.read(tables.written(dataset, table).key());
    }

    @GetMapping("/{table}/history")
    public List<Commit> history(@PathVariable String dataset, @PathVariable String table) {
        visibility.requireTable(dataset, table);
        return tables.history(dataset, table);
    }

    @GetMapping("/{table}/changes")
    public TableChanges.Changes changes(
            @PathVariable String dataset, @PathVariable String table, @RequestParam long from, @RequestParam long to) {
        visibility.requireTable(dataset, table);
        return changes.between(tables.written(dataset, table), from, to);
    }

    @GetMapping("/quarantine/reasons")
    public List<RuleCount> quarantineReasons(@PathVariable String dataset) {
        visibility.requireTable(dataset, Catalog.QUARANTINE);
        return quarantine.reasons(tables.source(dataset, Catalog.QUARANTINE));
    }

    @GetMapping("/bronze/rescued")
    public List<Rescued.Key> rescued(@PathVariable String dataset) {
        visibility.requireTable(dataset, Catalog.BRONZE);
        return rescued.keys(tables.source(dataset, Catalog.BRONZE));
    }

    @GetMapping("/landing/files")
    public QueryResult landing(@PathVariable String dataset) {
        visibility.requireTable(dataset, Catalog.LANDING);
        return tables.landing(dataset);
    }
}
