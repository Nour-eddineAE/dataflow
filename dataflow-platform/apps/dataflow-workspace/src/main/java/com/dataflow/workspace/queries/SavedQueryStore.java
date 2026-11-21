package com.dataflow.workspace.queries;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedQueryStore {

    List<SavedQuery> visibleTo(String person);

    Optional<SavedQuery> find(UUID id);

    void save(SavedQuery query);

    void delete(UUID id);
}
