package com.dataflow.engine.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@ConfigurationProperties("dataflow.spark")
@Validated
public record SparkProperties(
        @NotBlank String appName,
        Map<String, String> config,
        Map<String, Map<String, String>> stores,
        Map<String, String> checkpointManagers) {

    private static final String CHECKPOINT_MANAGER = "spark.sql.streaming.checkpointFileManagerClass";

    public SparkProperties {
        config = config == null ? Map.of() : config;
        stores = stores == null ? Map.of() : stores;
        checkpointManagers = checkpointManagers == null ? Map.of() : checkpointManagers;
    }

    private static String scheme(String root) {
        return root == null || root.isBlank() ? null : URI.create(root.strip()).getScheme();
    }

    public Map<String, String> settings(String lakeRoot, String... otherRoots) {
        Map<String, String> settings = new LinkedHashMap<>(config);
        Stream.concat(Stream.of(lakeRoot), Stream.of(otherRoots))
                .map(SparkProperties::scheme)
                .filter(Objects::nonNull)
                .distinct()
                .map(stores::get)
                .filter(Objects::nonNull)
                .forEach(settings::putAll);
        String scheme = scheme(lakeRoot);
        String manager = scheme == null ? null : checkpointManagers.get(scheme);
        if (manager != null) {
            settings.put(CHECKPOINT_MANAGER, manager);
        }
        settings.putAll(config);
        return settings;
    }
}
