package com.dataflow.workspace.impact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

interface ItemChanges<T> {

    String name(T item);

    Change added(T item);

    Optional<Change> changed(T before, T after);

    Change removed(T item);

    default List<Change> between(List<T> before, List<T> after) {
        Map<String, T> old = byName(before);
        Map<String, T> now = byName(after);
        List<Change> changes = new ArrayList<>();
        now.forEach((name, item) -> {
            T previous = old.get(name);
            if (previous == null) {
                changes.add(added(item));
            } else {
                changed(previous, item).ifPresent(changes::add);
            }
        });
        old.forEach((name, item) -> {
            if (!now.containsKey(name)) {
                changes.add(removed(item));
            }
        });
        return changes;
    }

    private Map<String, T> byName(List<T> items) {
        return items.stream()
                .collect(Collectors.toMap(this::name, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
}
