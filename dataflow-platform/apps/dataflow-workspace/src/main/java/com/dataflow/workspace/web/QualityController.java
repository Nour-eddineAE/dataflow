package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.runs.QualityRule;
import com.dataflow.workspace.runs.QualityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class QualityController {

    private static final int MAX_RULE_RUNS = 100;

    private final QualityService quality;
    private final Access access;

    public QualityController(QualityService quality, Access access) {
        this.quality = quality;
        this.access = access;
    }

    @GetMapping("/quality")
    public List<QualityRule> quality(
            @RequestParam(required = false) String dataset, @RequestParam(defaultValue = "20") int runs) {
        Caller caller = access.caller();
        return quality.rules(dataset, Limits.clamp(runs, MAX_RULE_RUNS)).stream()
                .filter(rule -> access.sees(caller, rule.dataset()))
                .toList();
    }
}
