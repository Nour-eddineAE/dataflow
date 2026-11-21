package com.dataflow.workspace.queries;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.Caller;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QueryAlerts {

    private static final Set<Integer> CHECK_INTERVALS = Set.of(5, 15, 60, 24 * 60);

    private final QueryAlertStore store;
    private final SavedQueries queries;
    private final Access access;

    public QueryAlerts(QueryAlertStore store, SavedQueries queries, Access access) {
        this.store = store;
        this.queries = queries;
        this.access = access;
    }

    public List<QueryAlertView> visible() {
        Map<UUID, SavedQuery> visible =
                queries.list().stream().collect(Collectors.toMap(SavedQuery::id, Function.identity()));
        return store.all().stream()
                .filter(alert -> visible.containsKey(alert.queryId()))
                .map(alert -> QueryAlertView.of(alert, visible.get(alert.queryId()).name()))
                .toList();
    }

    public void save(UUID queryId, int everyMinutes, QueryAlert.Comparison comparison, BigDecimal threshold) {
        if (!CHECK_INTERVALS.contains(everyMinutes)) {
            throw new IllegalArgumentException("An alert runs every 5 or 15 minutes, every hour or every day");
        }
        if (comparison == null || (comparison.compares() && threshold == null)) {
            throw new IllegalArgumentException("Say what to compare the first value with");
        }
        Caller caller = access.caller();
        requireOwner(queryId, caller);
        store.save(new QueryAlert(
                queryId,
                caller.id(),
                caller.groups(),
                caller.admin(),
                everyMinutes,
                comparison,
                comparison.compares() ? threshold : null,
                QueryAlert.State.UNKNOWN,
                null,
                null,
                null,
                null));
    }

    public void delete(UUID queryId) {
        requireOwner(queryId, access.caller());
        store.delete(queryId);
    }

    private void requireOwner(UUID queryId, Caller caller) {
        SavedQuery query = queries.get(queryId);
        if (!query.owner().equals(caller.id())) {
            throw new AccessDeniedException(caller.id() + " does not own " + query.name());
        }
    }
}
