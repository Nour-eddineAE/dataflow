package com.dataflow.spec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record GoldStepSpec(
        JoinSpec join,
        List<String> union,
        String filter,
        Map<String, String> derive,
        List<String> select,
        Map<String, String> rename,
        List<String> drop,
        AggregateSpec aggregate,
        DedupeSpec dedupe) {

    public GoldStepSpec {
        derive = ordered(derive);
        rename = ordered(rename);
    }

    private static Map<String, String> ordered(Map<String, String> map) {
        return map == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    public List<Operation> operations() {
        List<Operation> found = new ArrayList<>();
        add(found, join, Operation.JOIN);
        add(found, union, Operation.UNION);
        add(found, filter, Operation.FILTER);
        add(found, derive, Operation.DERIVE);
        add(found, select, Operation.SELECT);
        add(found, rename, Operation.RENAME);
        add(found, drop, Operation.DROP);
        add(found, aggregate, Operation.AGGREGATE);
        add(found, dedupe, Operation.DEDUPE);
        return found;
    }

    public Operation operation() {
        List<Operation> found = operations();
        if (found.size() != 1) {
            throw new IllegalStateException("A gold step holds exactly one operation, found " + found);
        }
        return found.getFirst();
    }

    public List<String> reads() {
        List<String> names = new ArrayList<>();
        if (join != null && join.with() != null) {
            names.add(join.with());
        }
        if (union != null) {
            names.addAll(union);
        }
        return names;
    }

    private static void add(List<Operation> found, Object value, Operation operation) {
        if (value != null) {
            found.add(operation);
        }
    }

    public enum Operation {
        JOIN,
        UNION,
        FILTER,
        DERIVE,
        SELECT,
        RENAME,
        DROP,
        AGGREGATE,
        DEDUPE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
