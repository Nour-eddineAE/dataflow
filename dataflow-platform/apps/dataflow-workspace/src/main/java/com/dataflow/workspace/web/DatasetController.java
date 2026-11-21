package com.dataflow.workspace.web;

import com.dataflow.spec.Names;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AuditAction;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.access.Visibility;
import com.dataflow.workspace.datasets.DatasetService;
import com.dataflow.workspace.datasets.DatasetSummary;
import com.dataflow.workspace.datasets.PipelineGraph;
import com.dataflow.workspace.lineage.ColumnLineage;
import com.dataflow.workspace.lineage.Lineage;
import com.dataflow.workspace.runs.FlowCell;
import com.dataflow.workspace.runs.RunRuntime;
import com.dataflow.workspace.runs.Partitions;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.runs.ScheduleState;
import com.dataflow.workspace.runs.Schedules;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.why.Why;
import com.dataflow.workspace.why.WhyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private static final int MAX_FLOW_RUNS = 50;
    private static final String DEFAULT_FLOW_RUNS = "20";

    private final DatasetService datasets;
    private final RunService runs;
    private final SpecStore specs;
    private final WhyService why;
    private final Schedules schedules;
    private final Access access;
    private final Visibility visibility;

    public DatasetController(
            DatasetService datasets,
            RunService runs,
            SpecStore specs,
            WhyService why,
            Schedules schedules,
            Access access,
            Visibility visibility) {
        this.access = access;
        this.visibility = visibility;
        this.datasets = datasets;
        this.runs = runs;
        this.specs = specs;
        this.why = why;
        this.schedules = schedules;
    }

    private static String checkedDataset(String dataset) {
        if (dataset == null || !Names.DATASET.matcher(dataset).matches()) {
            throw new IllegalArgumentException("A dataset name is a-z, 0-9 and _, starting with a letter");
        }
        return dataset;
    }

    @GetMapping
    public List<DatasetSummary> all() {
        Caller caller = access.caller();
        return datasets.all().stream()
                .filter(summary -> access.sees(caller, summary.dataset()))
                .toList();
    }

    @GetMapping("/{dataset}")
    public DatasetSummary one(@PathVariable String dataset) {
        String datasetName = checkedDataset(dataset);
        visibility.requireDataset(datasetName);
        return datasets.summary(datasetName);
    }

    @GetMapping("/{dataset}/graph")
    public PipelineGraph graph(@PathVariable String dataset) {
        String datasetName = checkedDataset(dataset);
        visibility.requireDataset(datasetName);
        return datasets.graph(datasetName);
    }

    @GetMapping("/{dataset}/lineage")
    public Lineage lineage(@PathVariable String dataset) {
        String datasetName = checkedDataset(dataset);
        access.require(Privilege.SELECT, Securable.dataset(datasetName));
        PipelineSpec spec = specs.spec(datasetName);
        return specs.parsing().run(() -> ColumnLineage.of(spec));
    }

    @GetMapping("/{dataset}/partitions")
    public Partitions partitions(@PathVariable String dataset) {
        String datasetName = checkedDataset(dataset);
        visibility.requireDataset(datasetName);
        return runs.partitions(datasetName);
    }

    @GetMapping("/{dataset}/schedule")
    public ScheduleState schedule(@PathVariable String dataset) {
        String datasetName = checkedDataset(dataset);
        visibility.requireDataset(datasetName);
        return schedules.state(datasetName);
    }

    @PostMapping("/{dataset}/schedule/pause")
    public ScheduleState pause(@PathVariable String dataset) {
        String datasetName = checkedDataset(dataset);
        access.require(Privilege.RUN, Securable.dataset(datasetName));
        ScheduleState state = schedules.pause(datasetName);
        access.record(AuditAction.PAUSE, Securable.dataset(datasetName));
        return state;
    }

    @PostMapping("/{dataset}/schedule/resume")
    public ScheduleState resume(@PathVariable String dataset) {
        String datasetName = checkedDataset(dataset);
        access.require(Privilege.RUN, Securable.dataset(datasetName));
        ScheduleState state = schedules.resume(datasetName);
        access.record(AuditAction.RESUME, Securable.dataset(datasetName));
        return state;
    }

    @GetMapping("/{dataset}/runtime")
    public ResponseEntity<RunRuntime> runtime(@PathVariable String dataset) {
        String datasetName = checkedDataset(dataset);
        visibility.requireDataset(datasetName);
        return runs.latestRuntime(datasetName).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{dataset}/flows")
    public List<FlowCell> flows(@PathVariable String dataset, @RequestParam(defaultValue = DEFAULT_FLOW_RUNS) int runs) {
        String datasetName = checkedDataset(dataset);
        visibility.requireDataset(datasetName);
        return this.runs.flowCells(datasetName, Limits.clamp(runs, MAX_FLOW_RUNS));
    }

    @GetMapping("/{dataset}/why")
    public Why why(
            @PathVariable String dataset, @RequestParam String id, @RequestParam(required = false) String column) {
        String datasetName = checkedDataset(dataset);
        access.require(Privilege.SELECT, Securable.dataset(datasetName));
        return why.explain(datasetName, id, column);
    }
}
