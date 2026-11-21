package com.dataflow.workspace.queries;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.Caller;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class SavedQueries {

    private static final int MAX_NAME = 100;
    private static final int MAX_SQL = 100_000;

    private final SavedQueryStore store;
    private final Access access;
    private final Clock clock;

    public SavedQueries(SavedQueryStore store, Access access, Clock clock) {
        this.store = store;
        this.access = access;
        this.clock = clock;
    }

    public List<SavedQuery> list() {
        return store.visibleTo(access.caller().id());
    }

    public SavedQuery get(UUID id) {
        SavedQuery query = required(id);
        Caller caller = access.caller();
        if (!query.shared() && !query.owner().equals(caller.id()) && !caller.admin()) {
            throw new AccessDeniedException(caller.id() + " cannot open " + id);
        }
        return query;
    }

    public SavedQuery save(UUID id, String name, String sql, boolean shared) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("A saved query needs a name of 1 to " + MAX_NAME + " characters");
        }
        if (sql == null || sql.isBlank() || sql.length() > MAX_SQL) {
            throw new IllegalArgumentException("A saved query needs its SQL (at most " + MAX_SQL + " characters)");
        }
        Caller caller = access.caller();
        if (id != null) {
            SavedQuery existing = required(id);
            if (!existing.owner().equals(caller.id())) {
                throw new AccessDeniedException(caller.id() + " does not own " + existing.name());
            }
        }
        SavedQuery query = new SavedQuery(
                id == null ? UUID.randomUUID() : id, caller.id(), name.strip(), sql, shared, clock.instant());
        store.save(query);
        return query;
    }

    public void delete(UUID id) {
        SavedQuery existing = required(id);
        Caller caller = access.caller();
        if (!existing.owner().equals(caller.id()) && !caller.admin()) {
            throw new AccessDeniedException(caller.id() + " does not own " + existing.name());
        }
        store.delete(id);
    }

    private SavedQuery required(UUID id) {
        return store.find(id).orElseThrow(() -> new IllegalArgumentException("No saved query " + id));
    }
}
