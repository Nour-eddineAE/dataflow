package com.dataflow.workspace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("dataflow.workspace.storage-access")
public record StorageAccessProperties(
        boolean enabled, String endpoint, String bucket, String browserUrl, Duration timeout, List<String> legacyPolicies) {

    private static final int MAX_PORT = 65535;

    public StorageAccessProperties {
        legacyPolicies = legacyPolicies == null ? List.of() : List.copyOf(legacyPolicies);
        if (legacyPolicies.stream().anyMatch(name -> !List.of("readonly", "readwrite").contains(name))) {
            throw new IllegalArgumentException("Only readonly and readwrite are legacy storage policies");
        }
        if (enabled) {
            endpoint = origin(endpoint);
            browserUrl = origin(browserUrl);
            if (bucket == null || !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                    || timeout == null || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Storage access requires a bucket and a positive timeout");
            }
        }
    }

    private static String origin(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Storage access requires administration and browser URLs");
        }
        URI uri = URI.create(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !List.of("", "/").contains(uri.getRawPath()) || uri.getPort() == 0 || uri.getPort() > MAX_PORT) {
            throw new IllegalArgumentException("Storage endpoints must be HTTP(S) origins without credentials, paths or queries");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
