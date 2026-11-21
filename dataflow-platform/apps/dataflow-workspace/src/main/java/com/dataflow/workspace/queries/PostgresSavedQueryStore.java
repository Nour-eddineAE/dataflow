package com.dataflow.workspace.queries;

import com.dataflow.workspace.controlplane.ControlPlane;
import com.dataflow.workspace.controlplane.Tables;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresSavedQueryStore implements SavedQueryStore {

    private final DSLContext sql;

    public PostgresSavedQueryStore(ControlPlane controlPlane) {
        this.sql = controlPlane.sql();
    }

    private static SavedQuery query(Record row) {
        return new SavedQuery(
                row.get(Tables.SavedQuery.ID),
                row.get(Tables.SavedQuery.OWNER),
                row.get(Tables.SavedQuery.NAME),
                row.get(Tables.SavedQuery.SQL),
                row.get(Tables.SavedQuery.SHARED),
                row.get(Tables.SavedQuery.UPDATED_AT).toInstant());
    }

    @Override
    public List<SavedQuery> visibleTo(String person) {
        return sql.selectFrom(Tables.SavedQuery.TABLE)
                .where(Tables.SavedQuery.OWNER.eq(person).or(Tables.SavedQuery.SHARED.isTrue()))
                .orderBy(Tables.SavedQuery.NAME)
                .fetch(PostgresSavedQueryStore::query);
    }

    @Override
    public Optional<SavedQuery> find(UUID id) {
        return sql.selectFrom(Tables.SavedQuery.TABLE)
                .where(Tables.SavedQuery.ID.eq(id))
                .fetchOptional(PostgresSavedQueryStore::query);
    }

    @Override
    public void save(SavedQuery query) {
        sql.insertInto(Tables.SavedQuery.TABLE)
                .set(Tables.SavedQuery.ID, query.id())
                .set(Tables.SavedQuery.OWNER, query.owner())
                .set(Tables.SavedQuery.NAME, query.name())
                .set(Tables.SavedQuery.SQL, query.sql())
                .set(Tables.SavedQuery.SHARED, query.shared())
                .set(Tables.SavedQuery.UPDATED_AT, query.updatedAt().atOffset(ZoneOffset.UTC))
                .onConflict(Tables.SavedQuery.ID)
                .doUpdate()
                .set(Tables.SavedQuery.NAME, query.name())
                .set(Tables.SavedQuery.SQL, query.sql())
                .set(Tables.SavedQuery.SHARED, query.shared())
                .set(Tables.SavedQuery.UPDATED_AT, query.updatedAt().atOffset(ZoneOffset.UTC))
                .execute();
    }

    @Override
    public void delete(UUID id) {
        sql.deleteFrom(Tables.SavedQuery.TABLE)
                .where(Tables.SavedQuery.ID.eq(id))
                .execute();
    }
}
