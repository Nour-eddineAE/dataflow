package com.dataflow.workspace.queries;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QueryAlertView(
        UUID queryId,
        String name,
        String owner,
        int everyMinutes,
        QueryAlert.Comparison comparison,
        BigDecimal threshold,
        QueryAlert.State state,
        Instant checkedAt,
        Instant since,
        String value,
        String error) {

    static QueryAlertView of(QueryAlert alert, String name) {
        return new QueryAlertView(
                alert.queryId(),
                name,
                alert.owner(),
                alert.everyMinutes(),
                alert.comparison(),
                alert.threshold(),
                alert.state(),
                alert.checkedAt(),
                alert.since(),
                alert.value(),
                alert.error());
    }
}
