package com.dataflow.engine.core.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlowResult {

    private final Map<String, Object> details = new LinkedHashMap<>();
    private final List<Materialization> materializations = new ArrayList<>();
    private final List<CheckResult> checks = new ArrayList<>();
    private long rowsIn;
    private long rowsOut;
    private long rowsDropped;

    public FlowResult rows(long in, long out, long dropped) {
        rowsIn += in;
        rowsOut += out;
        rowsDropped += dropped;
        return this;
    }

    public FlowResult detail(String key, Object value) {
        details.put(key, value);
        return this;
    }

    public FlowResult materialized(String assetKey, Map<String, Object> metadata) {
        materializations.add(new Materialization(assetKey, metadata));
        return this;
    }

    public FlowResult checked(CheckResult check) {
        checks.add(check);
        return this;
    }

    public long rowsIn() {
        return rowsIn;
    }

    public long rowsOut() {
        return rowsOut;
    }

    public long rowsDropped() {
        return rowsDropped;
    }

    public Map<String, Object> details() {
        return details;
    }

    public List<Materialization> materializations() {
        return materializations;
    }

    public List<CheckResult> checks() {
        return checks;
    }
}
