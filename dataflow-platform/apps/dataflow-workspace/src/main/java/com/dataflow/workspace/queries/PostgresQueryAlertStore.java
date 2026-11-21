package com.dataflow.workspace.queries;

import com.dataflow.workspace.controlplane.ControlPlane;
import com.dataflow.workspace.controlplane.Tables;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Repository
public class PostgresQueryAlertStore implements QueryAlertStore {

    private static final String GROUP_SEPARATOR = "\n";

    private final DSLContext sql;

    public PostgresQueryAlertStore(ControlPlane controlPlane) {
        this.sql = controlPlane.sql();
    }

    private static QueryAlert alert(Record row) {
        String groups = row.get(Tables.QueryAlert.OWNER_GROUPS);
        return new QueryAlert(
                row.get(Tables.QueryAlert.QUERY_ID),
                row.get(Tables.QueryAlert.OWNER),
                groups.isEmpty() ? List.of() : Arrays.asList(groups.split(GROUP_SEPARATOR)),
                row.get(Tables.QueryAlert.OWNER_ADMIN),
                row.get(Tables.QueryAlert.EVERY_MINUTES),
                QueryAlert.Comparison.valueOf(row.get(Tables.QueryAlert.COMPARISON)),
                row.get(Tables.QueryAlert.THRESHOLD),
                QueryAlert.State.valueOf(row.get(Tables.QueryAlert.STATE)),
                instant(row.get(Tables.QueryAlert.CHECKED_AT)),
                instant(row.get(Tables.QueryAlert.SINCE)),
                row.get(Tables.QueryAlert.VALUE),
                row.get(Tables.QueryAlert.ERROR));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime offset(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    @Override
    public List<QueryAlert> all() {
        return sql.selectFrom(Tables.QueryAlert.TABLE).fetch(PostgresQueryAlertStore::alert);
    }

    @Override
    public void save(QueryAlert alert) {
        sql.insertInto(Tables.QueryAlert.TABLE)
                .set(Tables.QueryAlert.QUERY_ID, alert.queryId())
                .set(Tables.QueryAlert.OWNER, alert.owner())
                .set(Tables.QueryAlert.OWNER_GROUPS, String.join(GROUP_SEPARATOR, alert.groups()))
                .set(Tables.QueryAlert.OWNER_ADMIN, alert.admin())
                .set(Tables.QueryAlert.EVERY_MINUTES, alert.everyMinutes())
                .set(Tables.QueryAlert.COMPARISON, alert.comparison().name())
                .set(Tables.QueryAlert.THRESHOLD, alert.threshold())
                .set(Tables.QueryAlert.STATE, alert.state().name())
                .onConflict(Tables.QueryAlert.QUERY_ID)
                .doUpdate()
                .set(Tables.QueryAlert.OWNER_GROUPS, String.join(GROUP_SEPARATOR, alert.groups()))
                .set(Tables.QueryAlert.OWNER_ADMIN, alert.admin())
                .set(Tables.QueryAlert.EVERY_MINUTES, alert.everyMinutes())
                .set(Tables.QueryAlert.COMPARISON, alert.comparison().name())
                .set(Tables.QueryAlert.THRESHOLD, alert.threshold())
                .set(Tables.QueryAlert.STATE, alert.state().name())
                .set(Tables.QueryAlert.CHECKED_AT, (OffsetDateTime) null)
                .set(Tables.QueryAlert.SINCE, (OffsetDateTime) null)
                .set(Tables.QueryAlert.VALUE, (String) null)
                .set(Tables.QueryAlert.ERROR, (String) null)
                .execute();
    }

    @Override
    public void delete(UUID queryId) {
        sql.deleteFrom(Tables.QueryAlert.TABLE)
                .where(Tables.QueryAlert.QUERY_ID.eq(queryId))
                .execute();
    }

    @Override
    public boolean claim(UUID queryId, Instant previous, Instant at) {
        Condition unchanged = previous == null
                ? Tables.QueryAlert.CHECKED_AT.isNull()
                : Tables.QueryAlert.CHECKED_AT.eq(offset(previous));
        return sql.update(Tables.QueryAlert.TABLE)
                        .set(Tables.QueryAlert.CHECKED_AT, offset(at))
                        .where(Tables.QueryAlert.QUERY_ID.eq(queryId).and(unchanged))
                        .execute()
                == 1;
    }

    @Override
    public void record(QueryAlert alert) {
        sql.update(Tables.QueryAlert.TABLE)
                .set(Tables.QueryAlert.STATE, alert.state().name())
                .set(Tables.QueryAlert.CHECKED_AT, offset(alert.checkedAt()))
                .set(Tables.QueryAlert.SINCE, offset(alert.since()))
                .set(Tables.QueryAlert.VALUE, alert.value())
                .set(Tables.QueryAlert.ERROR, alert.error())
                .where(Tables.QueryAlert.QUERY_ID.eq(alert.queryId()))
                .execute();
    }
}
