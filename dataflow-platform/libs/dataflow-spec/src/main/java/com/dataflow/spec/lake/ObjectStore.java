package com.dataflow.spec.lake;

import java.net.URI;

public enum ObjectStore {
    S3,
    AZURE,
    GCS;

    public static ObjectStore of(String root) {
        return switch (URI.create(root).getScheme()) {
            case "s3a", "s3" -> S3;
            case "abfss" -> AZURE;
            case "gs" -> GCS;
            case null, default -> throw new IllegalArgumentException("Unsupported object store root: " + root);
        };
    }

    public static String prefix(String root) {
        String path = URI.create(root).getPath();
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "";
        }
        String prefix = path.startsWith("/") ? path.substring(1) : path;
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }
}
