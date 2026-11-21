package com.dataflow.workspace.releases;

import com.dataflow.workspace.controlplane.ControlPlane;
import com.dataflow.workspace.controlplane.Tables;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;

@Repository
public class PostgresReleaseLog implements ReleaseLog {

    private final DSLContext sql;

    public PostgresReleaseLog(ControlPlane controlPlane) {
        this.sql = controlPlane.sql();
    }

    @Override
    public void record(ReleaseRecord release) {
        sql.insertInto(Tables.Release.TABLE)
                .set(Tables.Release.PROJECT, release.project())
                .set(Tables.Release.TARGET, release.target())
                .set(Tables.Release.COMMIT, release.commit())
                .set(Tables.Release.FOLDER_ID, release.folderId())
                .set(Tables.Release.RELEASED_BY, release.releasedBy())
                .set(Tables.Release.RELEASED_AT, release.releasedAt().atOffset(ZoneOffset.UTC))
                .execute();
    }

    @Override
    public List<ReleaseRecord> history(String project, int limit) {
        return sql.select()
                .from(Tables.Release.TABLE)
                .where(Tables.Release.PROJECT.eq(project))
                .orderBy(Tables.Release.RELEASED_AT.desc())
                .limit(limit)
                .fetch(row -> new ReleaseRecord(
                        row.get(Tables.Release.PROJECT),
                        row.get(Tables.Release.TARGET),
                        row.get(Tables.Release.COMMIT),
                        row.get(Tables.Release.FOLDER_ID),
                        row.get(Tables.Release.RELEASED_BY),
                        row.get(Tables.Release.RELEASED_AT).toInstant()));
    }
}
