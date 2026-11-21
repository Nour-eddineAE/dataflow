package com.dataflow.workspace.releases;

import com.dataflow.workspace.config.WorkspaceProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Component
public class S3ReleaseStore implements ReleaseStore, AutoCloseable {

    private static final int DELETE_BATCH = 1000;

    private final S3Client s3;
    private final String bucket;

    public S3ReleaseStore(WorkspaceProperties properties) {
        this.s3 = client(properties.releases());
        this.bucket = properties.releases().bucket();
    }

    public static S3Client client(WorkspaceProperties.Releases releases) {
        S3ClientBuilder builder =
                S3Client.builder().region(Region.of(releases.region())).forcePathStyle(releases.pathStyle());
        if (releases.endpoint() != null && !releases.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(releases.endpoint()));
        }
        if (releases.accessKey() != null && !releases.accessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(releases.accessKey(), releases.secretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }

    @Override
    public void put(String key, byte[] content) {
        s3.putObject(request -> request.bucket(bucket).key(key), RequestBody.fromBytes(content));
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(
                    s3.getObjectAsBytes(request -> request.bucket(bucket).key(key))
                            .asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<String> projects() {
        return s3
                .listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .delimiter("/")
                        .build())
                .commonPrefixes()
                .stream()
                .map(CommonPrefix::prefix)
                .map(prefix -> prefix.substring(0, prefix.length() - 1))
                .sorted()
                .toList();
    }

    @Override
    public List<StoredObject> list(String prefix) {
        return s3
                .listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .build())
                .contents()
                .stream()
                .map(object -> new StoredObject(object.key(), object.size(), object.lastModified()))
                .toList();
    }

    @Override
    public void delete(List<String> keys) {
        for (int start = 0; start < keys.size(); start += DELETE_BATCH) {
            List<ObjectIdentifier> batch = keys.subList(start, Math.min(keys.size(), start + DELETE_BATCH)).stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();
            s3.deleteObjects(request -> request.bucket(bucket)
                    .delete(Delete.builder().objects(batch).quiet(true).build()));
        }
    }

    @Override
    public void close() {
        s3.close();
    }
}
