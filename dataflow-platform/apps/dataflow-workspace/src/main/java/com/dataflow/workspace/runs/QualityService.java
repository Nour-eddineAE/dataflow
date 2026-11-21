package com.dataflow.workspace.runs;

import com.dataflow.spec.ExpectationSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.specs.SpecStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QualityService {

    private final SpecStore specs;
    private final EventLog events;

    public QualityService(SpecStore specs, EventLog events) {
        this.specs = specs;
        this.events = events;
    }

    public List<QualityRule> rules(String only, int runs) {
        List<QualityRule> rules = new ArrayList<>();
        for (PipelineSpec spec : specs.valid()) {
            if (only != null && !only.equals(spec.dataset())) {
                continue;
            }
            Map<String, List<RuleRun>> history =
                    events.ruleRuns(spec.dataset(), runs).stream().collect(Collectors.groupingBy(RuleRun::rule));
            for (ExpectationSpec rule : spec.silver().expectations()) {
                List<RuleRun> points = history.getOrDefault(rule.name(), List.of());
                rules.add(new QualityRule(
                        spec.dataset(),
                        rule.name(),
                        rule.check(),
                        rule.onFailure().name().toLowerCase(),
                        points,
                        points.isEmpty() ? null : points.getLast()));
            }
        }
        return rules;
    }
}
