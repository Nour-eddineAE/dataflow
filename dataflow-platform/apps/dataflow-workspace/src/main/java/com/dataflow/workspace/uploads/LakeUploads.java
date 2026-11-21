package com.dataflow.workspace.uploads;

import com.dataflow.spec.lake.Lake;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.releases.S3ReleaseStore;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LakeUploads implements AutoCloseable {

    private static final Set<String> S3_SCHEMES = Set.of("s3", "s3a");
    private static final String UNNAMED = "upload";

    private final S3Client s3;
    private final String root;

    public LakeUploads(WorkspaceProperties properties) {
        this.s3 = S3ReleaseStore.client(properties.releases());
        this.root = properties.serving().lakeRoot();
    }

    static String safeName(String original) {
        String file = original == null ? "" : original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        String safe = file.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("\\.{2,}", ".").replaceAll("^[._-]+", "");
        return safe.isEmpty() ? UNNAMED : safe;
    }

    static String key(String person, UUID id, String original) {
        return Lake.uploadsOf(person) + id + "/" + safeName(original);
    }

    static Location location(String root, String key) {
        URI uri = URI.create(root);
        if (uri.getScheme() == null || !S3_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Files can be uploaded only to a lake on S3; this one is " + root);
        }
        String prefix = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+|/+$", "");
        return new Location(uri.getHost(), prefix.isEmpty() ? key : prefix + "/" + key);
    }

    public String store(String person, String name, long size, InputStream content) {
        String key = key(person, UUID.randomUUID(), name);
        Location target = location(root, key);
        s3.putObject(
                request -> request.bucket(target.bucket()).key(target.key()),
                RequestBody.fromInputStream(content, size));
        return key;
    }

    @Override
    public void close() {
        s3.close();
    }

    record Location(String bucket, String key) {}
}
