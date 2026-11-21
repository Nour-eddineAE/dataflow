package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AuditAction;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.access.Visibility;
import com.dataflow.workspace.runs.RunComparison;
import com.dataflow.workspace.runs.RunDetail;
import com.dataflow.workspace.runs.RunOutput;
import com.dataflow.workspace.runs.RunOutputs;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.runs.RunSummary;
import com.dataflow.workspace.runs.Schedules;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class RunController {


    private final RunService runs;
    private final RunOutputs outputs;
    private final Schedules schedules;
    private final Access access;
    private final Visibility visibility;

    public RunController(
            RunService runs, RunOutputs outputs, Schedules schedules, Access access, Visibility visibility) {
        this.access = access;
        this.visibility = visibility;
        this.runs = runs;
        this.outputs = outputs;
        this.schedules = schedules;
    }

    @GetMapping("/runs")
    public List<RunSummary> runs(
            @RequestParam(required = false) String dataset, @RequestParam(defaultValue = "50") int limit) {
        Caller caller = access.caller();
        return runs.runs(dataset, Limits.clamp(limit, Limits.MAX_RUNS)).stream()
                .filter(run -> sees(caller, run))
                .toList();
    }

    @GetMapping("/runs/{runId}")
    public RunDetail run(@PathVariable String runId) {
        RunDetail detail = runs.detail(runId);
        requireRun(detail.run());
        return detail;
    }

    @GetMapping("/runs/{runId}/previous")
    public RunComparison previous(@PathVariable String runId, @RequestParam String dataset) {
        visibility.requireDataset(dataset);
        return runs.previous(dataset, runId);
    }

    @GetMapping("/runs/{runId}/output")
    public RunOutput output(@PathVariable String runId) {
        requireRun(runs.summary(runId));
        return outputs.output(runId);
    }

    @PostMapping("/runs")
    public RunRef launch(@RequestBody Launch launch) {
        Securable dataset = Securable.dataset(launch.dataset());
        access.require(launch.refreshes() ? Privilege.EDIT : Privilege.RUN, dataset);
        RunRef run = new RunRef(
                runs.launch(launch.dataset(), launch.partition(), access.caller().id(), launch.refreshes()));
        access.record(launch.refreshes() ? AuditAction.FULL_REFRESH : AuditAction.RUN, dataset);
        return run;
    }

    @PostMapping("/runs/{runId}/terminate")
    public RunRef terminate(@PathVariable String runId) {
        RunSummary run = runs.summary(runId);
        if (run.dataset() != null) {
            access.require(Privilege.RUN, Securable.dataset(run.dataset()));
        } else {
            access.requireAdmin();
        }
        runs.terminate(runId);
        if (run.dataset() != null) {
            access.record(AuditAction.TERMINATE, Securable.dataset(run.dataset()));
        }
        return new RunRef(runId);
    }

    @GetMapping("/schedules")
    public Map<String, Boolean> schedules() {
        Caller caller = access.caller();
        return schedules.running().entrySet().stream()
                .filter(schedule -> access.sees(caller, schedule.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @PostMapping("/backfills")
    public BackfillRef backfill(@RequestBody Backfill backfill) {
        Securable dataset = Securable.dataset(backfill.dataset());
        access.require(Privilege.RUN, dataset);
        BackfillRef started =
                new BackfillRef(runs.backfill(backfill.dataset(), backfill.partitions(), access.caller().id()));
        access.record(AuditAction.BACKFILL, dataset);
        return started;
    }

    private boolean sees(Caller caller, RunSummary run) {
        return run.dataset() == null ? caller.admin() : access.sees(caller, run.dataset());
    }

    private void requireRun(RunSummary run) {
        if (run.dataset() == null) {
            access.requireAdmin();
        } else {
            visibility.requireDataset(run.dataset());
        }
    }

    public record Launch(String dataset, String partition, Boolean fullRefresh) {

        boolean refreshes() {
            return Boolean.TRUE.equals(fullRefresh);
        }
    }

    public record Backfill(String dataset, List<String> partitions) {}

    public record RunRef(String runId) {}

    public record BackfillRef(String backfillId) {}
}
