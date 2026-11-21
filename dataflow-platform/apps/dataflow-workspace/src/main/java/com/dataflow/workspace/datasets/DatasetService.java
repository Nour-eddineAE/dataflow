package com.dataflow.workspace.datasets;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.runs.RunSummary;
import com.dataflow.workspace.runs.TowerException;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.specs.StoredSpec;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatasetService {

    private static final int RECENT_RUNS = 100;

    private final SpecStore specs;
    private final RunService runs;

    public DatasetService(SpecStore specs, RunService runs) {
        this.specs = specs;
        this.runs = runs;
    }

    public List<DatasetSummary> all() {
        Map<String, RunSummary> latest = latestRuns();
        return specs.all().stream()
                .map(stored -> summary(
                        stored,
                        latest.containsKey(stored.dataset())
                                ? latest.get(stored.dataset())
                                : lastRun(stored.dataset())))
                .toList();
    }

    public DatasetSummary summary(String dataset) {
        return summary(specs.get(dataset), lastRun(dataset));
    }

    private Map<String, RunSummary> latestRuns() {
        Map<String, RunSummary> latest = new HashMap<>();
        try {
            runs.runs(null, RECENT_RUNS).stream()
                    .filter(run -> run.dataset() != null)
                    .forEach(run -> latest.putIfAbsent(run.dataset(), run));
        } catch (TowerException e) {
            return Map.of();
        }
        return latest;
    }

    private RunSummary lastRun(String dataset) {
        try {
            return runs.runs(dataset, 1).stream().findFirst().orElse(null);
        } catch (TowerException e) {
            return null;
        }
    }

    private DatasetSummary summary(StoredSpec stored, RunSummary last) {
        if (!stored.valid()) {
            return new DatasetSummary(
                    stored.dataset(),
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    List.of(),
                    last,
                    stored.problems());
        }
        PipelineSpec spec = stored.spec();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("columns", spec.silver().columns().size());
        counts.put("derived", spec.silver().derive().size());
        counts.put("expectations", spec.silver().expectations().size());
        counts.put("references", spec.references().size());
        counts.put("inputs", spec.inputs().size());
        counts.put("gold", spec.gold().size());
        counts.put("served", spec.serve().size());
        String partitions = spec.partitioned()
                ? spec.source().partitions().kind().name().toLowerCase() + " from "
                        + spec.source().partitions().start()
                : null;
        String source = !spec.ingests()
                ? String.join(", ", spec.inputs().values())
                : spec.source().url() != null ? spec.source().url() : spec.source().path();
        return new DatasetSummary(
                spec.dataset(),
                true,
                spec.version(),
                spec.owner(),
                spec.description(),
                spec.schedule(),
                partitions,
                source,
                spec.ingests() ? spec.source().format() : null,
                counts,
                spec.history().keys(),
                last,
                List.of());
    }

    public PipelineGraph graph(String dataset) {
        return PipelineGraphs.of(specs.spec(dataset), runs.latestFlows(dataset));
    }
}
