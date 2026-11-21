package com.dataflow.workspace.runs;

import java.util.List;

public record QualityRule(
        String dataset, String name, String check, String action, List<RuleRun> history, RuleRun latest) {}
