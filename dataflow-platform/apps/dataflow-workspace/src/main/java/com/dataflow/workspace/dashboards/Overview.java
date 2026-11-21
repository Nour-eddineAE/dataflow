package com.dataflow.workspace.dashboards;

import com.dataflow.workspace.catalog.RuleCount;
import com.dataflow.workspace.catalog.TableStorage;
import com.dataflow.workspace.runs.EventRun;
import com.dataflow.workspace.runs.FlowEvent;
import com.dataflow.workspace.runs.RuleRun;

import java.util.List;

public record Overview(
        String dataset,
        String schedule,
        Freshness freshness,
        List<EventRun> runs,
        List<FlowEvent> flows,
        List<RuleRun> rules,
        List<RuleCount> quarantine,
        List<TableStorage> storage) {}
