package com.dataflow.workspace.queries;

import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.ClickHouseRoles;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.QueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class QueryAlertChecks {

    private final QueryAlertStore alerts;
    private final SavedQueryStore queries;
    private final ClickHouse serving;
    private final ClickHouseRoles roles;
    private final Clock clock;

    public QueryAlertChecks(
            QueryAlertStore alerts, SavedQueryStore queries, ClickHouse serving, ClickHouseRoles roles, Clock clock) {
        this.alerts = alerts;
        this.queries = queries;
        this.serving = serving;
        this.roles = roles;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = WorkspaceProperties.SYNC_EVERY,
            initialDelayString = WorkspaceProperties.SYNC_EVERY)
    public void checkDue() {
        try {
            Instant now = clock.instant();
            alerts.all().stream().filter(alert -> alert.due(now)).forEach(alert -> check(alert, now));
        } catch (RuntimeException e) {
            log.warn("Query alerts not checked: {}", e.getMessage());
        }
    }

    private void check(QueryAlert alert, Instant now) {
        if (!alerts.claim(alert.queryId(), alert.checkedAt(), now)) {
            return;
        }
        alerts.record(evaluate(alert, now));
    }

    private QueryAlert evaluate(QueryAlert alert, Instant now) {
        Optional<SavedQuery> query = queries.find(alert.queryId());
        if (query.isEmpty()) {
            return alert.checked(QueryAlert.State.ERROR, now, null, "The saved query is gone");
        }
        try {
            QueryResult result = serving.query(query.get().sql(), serving.maxRows(), as(alert.caller()));
            boolean fires = alert.comparison().holds(result, alert.threshold());
            return alert.checked(
                    fires ? QueryAlert.State.FIRING : QueryAlert.State.OK, now, alert.comparison().value(result), null);
        } catch (RuntimeException e) {
            return alert.checked(QueryAlert.State.ERROR, now, null, e.getMessage());
        }
    }

    private ClickHouse.As as(Caller caller) {
        return caller.admin() ? serving.platform(ClickHouse.editorComment(caller.id())) : roles.as(caller);
    }
}
