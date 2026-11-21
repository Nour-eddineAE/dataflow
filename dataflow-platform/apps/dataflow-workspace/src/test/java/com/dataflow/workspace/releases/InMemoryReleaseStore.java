package com.dataflow.workspace.releases;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class InMemoryReleaseStore implements ReleaseStore {

    public final Map<String, byte[]> objects = new TreeMap<>();
    public final Map<String, Instant> modified = new TreeMap<>();
    private Instant clock = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public void put(String key, byte[] content) {
        objects.put(key, content.clone());
        clock = clock.plusSeconds(60);
        modified.put(key, clock);
    }

    @Override
    public Optional<byte[]> get(String key) {
        return Optional.ofNullable(objects.get(key));
    }

    @Override
    public List<String> projects() {
        return objects.keySet().stream()
                .map(key -> key.substring(0, key.indexOf('/')))
                .distinct()
                .toList();
    }

    @Override
    public List<StoredObject> list(String prefix) {
        return objects.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(entry -> new StoredObject(entry.getKey(), entry.getValue().length, modified.get(entry.getKey())))
                .toList();
    }

    @Override
    public void delete(List<String> keys) {
        keys.forEach(key -> {
            objects.remove(key);
            modified.remove(key);
        });
    }
}
