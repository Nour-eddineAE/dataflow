package com.dataflow.workspace.releases;

import com.dataflow.spec.ReleaseId;
import com.dataflow.workspace.folders.FolderViews.Snapshot;
import com.dataflow.workspace.folders.Person;
import com.dataflow.workspace.releases.ReleaseFiles.Manifest;
import com.dataflow.workspace.releases.ReleaseFiles.Pointer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ReleaseWriter {

    private final ReleaseStore store;
    private final ReleaseLog releaseLog;
    private final ObjectMapper json;
    private final Clock clock;

    public ReleaseWriter(ReleaseStore store, ReleaseLog releaseLog, ObjectMapper json, Clock clock) {
        this.store = store;
        this.releaseLog = releaseLog;
        this.json = json;
        this.clock = clock;
    }

    public Instant write(ReleaseId id, String target, Snapshot snapshot, UUID folderId, Person person) {
        Instant now = clock.instant();
        if (store.get(ReleaseFiles.manifest(id)).isEmpty()) {
            snapshot.files().forEach((path, content) -> store.put(ReleaseFiles.file(id, path), content));
            store.put(
                    ReleaseFiles.manifest(id),
                    bytes(new Manifest(
                            id.project(),
                            id.commit(),
                            target,
                            now,
                            person.id(),
                            List.copyOf(snapshot.files().keySet()))));
        }
        store.put(
                ReleaseFiles.pointer(id.project(), target),
                bytes(new Pointer(id.project(), target, id.commit(), now, person.id())));
        releaseLog.record(new ReleaseRecord(id.project(), target, id.commit(), folderId, person.id(), now));
        return now;
    }

    private byte[] bytes(Object value) {
        try {
            return json.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(value)
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }
}
