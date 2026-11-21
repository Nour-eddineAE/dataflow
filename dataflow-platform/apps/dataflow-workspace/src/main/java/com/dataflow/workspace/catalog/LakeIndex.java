package com.dataflow.workspace.catalog;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class LakeIndex {

    private static final Duration FRESH_FOR = Duration.ofSeconds(15);

    private final DeltaLog log;
    private final Clock clock;
    private volatile Map<String, TableLog> logs = Map.of();
    private volatile Instant readAt = Instant.EPOCH;

    public LakeIndex(DeltaLog log, Clock clock) {
        this.log = log;
        this.clock = clock;
    }

    public Map<String, TableLog> logs() {
        if (readAt.plus(FRESH_FOR).isBefore(clock.instant())) {
            refresh();
        }
        return logs;
    }

    public synchronized Map<String, TableLog> refresh() {
        logs = log.tables();
        readAt = clock.instant();
        return logs;
    }

    public List<LakeTable> locate(List<LakeTable> tables) {
        Map<String, TableLog> known = logs();
        return tables.stream()
                .map(table -> table.delta() ? table.located(known.get(table.key())) : table)
                .toList();
    }
}
