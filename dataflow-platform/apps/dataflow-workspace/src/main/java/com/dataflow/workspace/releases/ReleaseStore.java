package com.dataflow.workspace.releases;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReleaseStore {

    void put(String key, byte[] content);

    Optional<byte[]> get(String key);

    List<String> projects();

    List<StoredObject> list(String prefix);

    void delete(List<String> keys);

    record StoredObject(String key, long bytes, Instant modified) {}
}
