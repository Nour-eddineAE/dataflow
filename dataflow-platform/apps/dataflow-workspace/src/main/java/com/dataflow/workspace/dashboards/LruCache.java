package com.dataflow.workspace.dashboards;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class LruCache {

    private static final int LIMIT = 400;
    private static final float LOAD_FACTOR = 0.75f;

    private final Map<String, Object> entries;

    LruCache() {
        this.entries = Collections.synchronizedMap(new LinkedHashMap<>(LIMIT, LOAD_FACTOR, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
                return size() > LIMIT;
            }
        });
    }

    Object get(String key) {
        return entries.get(key);
    }

    void put(String key, Object value) {
        entries.put(key, value);
    }
}
