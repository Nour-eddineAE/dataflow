package com.dataflow.workspace.access;

import com.dataflow.workspace.controlplane.ControlPlane;
import com.dataflow.workspace.controlplane.Tables;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class PostgresAccessStore implements AccessStore {

    private final DSLContext sql;

    public PostgresAccessStore(ControlPlane controlPlane) {
        this.sql = controlPlane.sql();
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }

    @Override
    public List<Grant> grants() {
        return sql.selectFrom(Tables.AccessGrant.TABLE)
                .orderBy(Tables.AccessGrant.SECURABLE, Tables.AccessGrant.PRINCIPAL, Tables.AccessGrant.PRIVILEGE)
                .fetch(row -> new Grant(
                        Securable.parse(row.get(Tables.AccessGrant.SECURABLE)),
                        Principal.parse(row.get(Tables.AccessGrant.PRINCIPAL)),
                        Privilege.valueOf(row.get(Tables.AccessGrant.PRIVILEGE)),
                        row.get(Tables.AccessGrant.GRANTED_BY),
                        instant(row.get(Tables.AccessGrant.GRANTED_AT))));
    }

    @Override
    public void add(Grant grant) {
        sql.insertInto(Tables.AccessGrant.TABLE)
                .set(Tables.AccessGrant.SECURABLE, grant.securable().toString())
                .set(Tables.AccessGrant.PRINCIPAL, grant.principal().toString())
                .set(Tables.AccessGrant.PRIVILEGE, grant.privilege().name())
                .set(Tables.AccessGrant.GRANTED_BY, grant.grantedBy())
                .set(Tables.AccessGrant.GRANTED_AT, offset(grant.grantedAt()))
                .onConflictDoNothing()
                .execute();
    }

    @Override
    public boolean remove(Securable securable, Principal principal, Privilege privilege) {
        return sql.deleteFrom(Tables.AccessGrant.TABLE)
                        .where(Tables.AccessGrant.SECURABLE.eq(securable.toString()))
                        .and(Tables.AccessGrant.PRINCIPAL.eq(principal.toString()))
                        .and(Tables.AccessGrant.PRIVILEGE.eq(privilege.name()))
                        .execute()
                > 0;
    }

    @Override
    public void audit(AuditEntry entry) {
        sql.insertInto(Tables.AccessAudit.TABLE)
                .set(Tables.AccessAudit.AT, offset(entry.at()))
                .set(Tables.AccessAudit.ACTOR, entry.actor())
                .set(Tables.AccessAudit.ACTION, entry.action())
                .set(Tables.AccessAudit.SECURABLE, entry.securable())
                .set(Tables.AccessAudit.PRINCIPAL, entry.principal())
                .set(Tables.AccessAudit.PRIVILEGE, entry.privilege())
                .execute();
    }

    @Override
    public List<AuditEntry> audits(int limit) {
        return sql.selectFrom(Tables.AccessAudit.TABLE)
                .orderBy(Tables.AccessAudit.ID.desc())
                .limit(limit)
                .fetch(row -> new AuditEntry(
                        instant(row.get(Tables.AccessAudit.AT)),
                        row.get(Tables.AccessAudit.ACTOR),
                        row.get(Tables.AccessAudit.ACTION),
                        row.get(Tables.AccessAudit.SECURABLE),
                        row.get(Tables.AccessAudit.PRINCIPAL),
                        row.get(Tables.AccessAudit.PRIVILEGE)));
    }
}
