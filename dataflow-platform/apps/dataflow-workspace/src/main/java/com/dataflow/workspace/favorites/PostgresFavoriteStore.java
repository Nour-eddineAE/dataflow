package com.dataflow.workspace.favorites;

import com.dataflow.workspace.controlplane.ControlPlane;
import com.dataflow.workspace.controlplane.Tables;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;

@Repository
public class PostgresFavoriteStore implements FavoriteStore {

    private final DSLContext sql;

    public PostgresFavoriteStore(ControlPlane controlPlane) {
        this.sql = controlPlane.sql();
    }

    private static Favorite favorite(Record row) {
        return new Favorite(
                Favorite.Kind.valueOf(row.get(Tables.Favorite.KIND)),
                row.get(Tables.Favorite.REF),
                row.get(Tables.Favorite.CREATED_AT).toInstant());
    }

    @Override
    public List<Favorite> of(String owner) {
        return sql.selectFrom(Tables.Favorite.TABLE)
                .where(Tables.Favorite.OWNER.eq(owner))
                .orderBy(Tables.Favorite.CREATED_AT.desc())
                .fetch(PostgresFavoriteStore::favorite);
    }

    @Override
    public void add(String owner, Favorite favorite) {
        sql.insertInto(Tables.Favorite.TABLE)
                .set(Tables.Favorite.OWNER, owner)
                .set(Tables.Favorite.KIND, favorite.kind().name())
                .set(Tables.Favorite.REF, favorite.ref())
                .set(Tables.Favorite.CREATED_AT, favorite.createdAt().atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute();
    }

    @Override
    public void remove(String owner, Favorite.Kind kind, String ref) {
        sql.deleteFrom(Tables.Favorite.TABLE)
                .where(Tables.Favorite.OWNER.eq(owner))
                .and(Tables.Favorite.KIND.eq(kind.name()))
                .and(Tables.Favorite.REF.eq(ref))
                .execute();
    }
}
