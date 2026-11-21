package com.dataflow.workspace.queries;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QueryAlertStore {

    List<QueryAlert> all();

    void save(QueryAlert alert);

    void delete(UUID queryId);

    boolean claim(UUID queryId, Instant previous, Instant at);

    void record(QueryAlert alert);
}
