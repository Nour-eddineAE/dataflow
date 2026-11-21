package com.dataflow.workspace.access;

import com.dataflow.workspace.config.StorageAccessProperties;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.specs.SpecStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.signer.AwsS3V4Signer;
import software.amazon.awssdk.auth.signer.params.AwsS3V4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RustFsPolicies {

    private final StorageAccessProperties settings;
    private final WorkspaceProperties properties;
    private final Access access;
    private final SpecStore specs;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Clock clock;
    private volatile Status status;

    public RustFsPolicies(StorageAccessProperties settings, WorkspaceProperties properties, Access access,
                          SpecStore specs, ObjectMapper json, HttpClient http, Clock clock) {
        this.settings = settings;
        this.properties = properties;
        this.access = access;
        this.specs = specs;
        this.json = json;
        this.http = http;
        this.clock = clock;
        this.status = new Status(settings.enabled(), null, null);
    }

    public Status status() {
        return status;
    }

    public List<DatasetPaths> paths(Caller caller) {
        if (!settings.enabled()) {
            return List.of();
        }
        return StoragePolicyDocument.datasets(caller, access.grants(), specs.valid()).stream()
                .map(dataset -> new DatasetPaths(dataset, StoragePolicyDocument.prefixes(dataset).stream()
                        .map(prefix -> new LayerPath(prefix, settings.browserUrl() + "/rustfs/console/browser/?bucket="
                                + settings.bucket() + "&key=" + URLEncoder.encode(prefix, StandardCharsets.UTF_8)))
                        .toList()))
                .toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        reconcile();
    }

    @EventListener
    public void onChange(Access.Changed changed) {
        try {
            apply();
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Grant changes were saved. " + e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = WorkspaceProperties.SYNC_EVERY,
            initialDelayString = WorkspaceProperties.SYNC_EVERY)
    public void reconcile() {
        try {
            apply();
        } catch (RuntimeException e) {
            log.warn("Storage policies not applied: {}", e.getMessage());
        }
    }

    public synchronized void apply() {
        if (!settings.enabled()) {
            return;
        }
        try {
            WorkspaceProperties.Auth auth = properties.auth();
            if (!"groups".equals(auth.groupsClaim()) || !"preferred_username".equals(auth.usernameClaim())) {
                throw new IllegalStateException("Scoped RustFS access requires groups and preferred_username claims matching the console");
            }
            WorkspaceProperties.Releases credentials = properties.releases();
            if (credentials.accessKey() == null || credentials.accessKey().isBlank()
                    || credentials.secretKey() == null || credentials.secretKey().isBlank()) {
                throw new IllegalStateException("Storage policy administration credentials are required");
            }
            Map<String, Object> document = StoragePolicyDocument.build(
                    access.grants(), specs.valid(), auth.admins(), settings.bucket());
            byte[] body = json.writeValueAsBytes(document);
            for (String policy : settings.legacyPolicies()) {
                publish(policy, body, credentials);
            }
            publish(StoragePolicyDocument.NAME, body, credentials);
            status = new Status(true, clock.instant(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed("Storage policy update interrupted");
        } catch (IOException | RuntimeException e) {
            failed(e instanceof IOException ? "Storage policy endpoint is unavailable" : e.getMessage());
        }
    }

    private void publish(String policy, byte[] body, WorkspaceProperties.Releases credentials) throws IOException, InterruptedException {
        SdkHttpFullRequest unsigned = SdkHttpFullRequest.builder().uri(URI.create(settings.endpoint()))
                .method(SdkHttpMethod.PUT).encodedPath("/rustfs/admin/v3/add-canned-policy")
                .putRawQueryParameter("name", policy)
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .contentStreamProvider(() -> new ByteArrayInputStream(body)).build();
        SdkHttpFullRequest signed = AwsS3V4Signer.create().sign(unsigned, AwsS3V4SignerParams.builder()
                .awsCredentials(AwsBasicCredentials.create(credentials.accessKey(), credentials.secretKey()))
                .signingName("s3").signingRegion(Region.of(credentials.region()))
                .signingClockOverride(clock).enablePayloadSigning(true).build());
        HttpRequest.Builder request = HttpRequest.newBuilder(signed.getUri()).timeout(settings.timeout())
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        signed.headers().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length")) {
                values.forEach(value -> request.header(name, value));
            }
        });
        HttpResponse<Void> response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != HttpStatus.OK.value()) {
            throw new IllegalStateException("RustFS policy update returned HTTP " + response.statusCode());
        }
    }

    private void failed(String message) {
        status = new Status(true, status.appliedAt(), message);
        throw new IllegalStateException("Storage permissions were not applied: " + message);
    }

    public record Status(boolean enabled, Instant appliedAt, String problem) {}

    public record DatasetPaths(String dataset, List<LayerPath> paths) {}

    public record LayerPath(String prefix, String url) {}
}
