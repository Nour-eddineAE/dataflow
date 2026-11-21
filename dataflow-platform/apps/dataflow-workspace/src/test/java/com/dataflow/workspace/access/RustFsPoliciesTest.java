package com.dataflow.workspace.access;

import com.dataflow.workspace.Drafts;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.config.StorageAccessProperties;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.specs.SpecStore;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RustFsPoliciesTest {

    private static final Instant NOW = Instant.parse("2026-10-29T20:00:00Z");
    private final Access access = mock(Access.class);
    private final SpecStore specs = mock(SpecStore.class);
    private final AtomicInteger response = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final List<String> names = new CopyOnWriteArrayList<>();
    private final WorkspaceProperties properties = mock(WorkspaceProperties.class);
    private HttpServer server;
    private RustFsPolicies policies;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rustfs/admin/v3/add-canned-policy", exchange -> {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            assertThat(exchange.getRequestMethod()).isEqualTo("PUT");
            String query = exchange.getRequestURI().getQuery();
            assertThat(query).isIn("name=dataflow_datasets", "name=readonly", "name=readwrite");
            names.add(query);
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                    .contains("AWS4-HMAC-SHA256 Credential=policy-admin/20261029/us-east-1/s3/aws4_request")
                    .doesNotContain("test-policy-secret");
            assertThat(exchange.getRequestHeaders().getFirst("X-Amz-Content-Sha256"))
                    .isEqualTo(sha256(bytes));
            body.set(new String(bytes, StandardCharsets.UTF_8));
            calls.incrementAndGet();
            exchange.sendResponseHeaders(response.get(), -1);
            exchange.close();
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        when(properties.releases()).thenReturn(new WorkspaceProperties.Releases(
                "releases", endpoint, "policy-admin", "test-policy-secret", "prod", "/tmp/releases",
                Duration.ofMinutes(1), "us-east-1", true));
        when(properties.auth()).thenReturn(Workspace.properties().auth());
        when(access.grants()).thenReturn(List.of());
        when(specs.valid()).thenReturn(List.of(Drafts.spec("earthquakes")));
        policies = new RustFsPolicies(new StorageAccessProperties(true, endpoint, "lake", "http://console", Duration.ofSeconds(2), List.of()),
                properties, access, specs, JsonMapper.builder().build(), HttpClient.newHttpClient(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void grantAndRevocationReplaceTheSameNamedPolicy() {
        when(access.grants()).thenReturn(List.of(new Grant(Securable.dataset("earthquakes"),
                Principal.group("analysts"), Privilege.SELECT, "admin", NOW)));
        policies.onChange(new Access.Changed(List.of()));
        assertThat(body.get()).contains("bronze/earthquakes/*");
        assertThat(policies.status().appliedAt()).isEqualTo(NOW);

        when(access.grants()).thenReturn(List.of());
        policies.onChange(new Access.Changed(List.of()));

        assertThat(body.get()).doesNotContain("earthquakes");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void updateFailuresAreVisibleAndReconciliationRecovers() {
        response.set(503);

        assertThatThrownBy(policies::apply).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Storage permissions were not applied").hasMessageContaining("HTTP 503")
                .hasMessageNotContaining("Grant changes were saved");
        assertThat(policies.status().problem()).contains("HTTP 503");
        assertThat(policies.status().appliedAt()).isNull();

        response.set(200);
        policies.reconcile();

        assertThat(policies.status().problem()).isNull();
        assertThat(policies.status().appliedAt()).isEqualTo(NOW);
    }

    @Test
    void aFailedGrantUpdateExplainsThatTheGrantWasSavedAndRetainsTheLastAppliedTime() {
        policies.apply();
        response.set(503);

        assertThatThrownBy(() -> policies.onChange(new Access.Changed(List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Grant changes were saved").hasMessageContaining("HTTP 503");
        assertThat(policies.status().appliedAt()).isEqualTo(NOW);
        assertThat(policies.status().problem()).contains("HTTP 503");
    }

    @Test
    void aDifferentWorkspaceIdentityMappingCannotPublishMismatchedPolicies() {
        for (String[] claims : List.of(new String[]{"roles", "preferred_username"}, new String[]{"groups", "sub"})) {
            when(properties.auth()).thenReturn(new WorkspaceProperties.Auth(
                    WorkspaceProperties.AuthMode.OIDC, "http://issuer", "http://issuer", "workspace", "unused",
                    claims[0], claims[1], List.of("admins")));

            assertThatThrownBy(policies::apply).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("groups and preferred_username");
        }
        assertThat(calls.get()).isZero();
    }

    @Test
    void datasetPathsOpenTheConsoleAtEachLayerPrefix() {
        when(access.grants()).thenReturn(List.of(new Grant(Securable.dataset("earthquakes"),
                Principal.group("analysts"), Privilege.SELECT, "admin", NOW)));

        assertThat(policies.paths(new Caller("ada", List.of("analysts"), false))).singleElement()
                .satisfies(dataset -> assertThat(dataset.paths()).contains(new RustFsPolicies.LayerPath(
                        "bronze/earthquakes/", "http://console/rustfs/console/browser/?bucket=lake&key=bronze%2Fearthquakes%2F")));
    }

    @Test
    void disabledStorageDoesNotPublishOrExposeDatasetPaths() {
        RustFsPolicies disabled = new RustFsPolicies(
                new StorageAccessProperties(false, null, null, null, null, List.of()), properties, access, specs,
                JsonMapper.builder().build(), HttpClient.newHttpClient(), Clock.fixed(NOW, ZoneOffset.UTC));

        disabled.apply();
        disabled.onChange(new Access.Changed(List.of()));

        assertThat(disabled.paths(new Caller("admin", List.of(), true))).isEmpty();
        assertThat(disabled.status().enabled()).isFalse();
        assertThat(calls.get()).isZero();
    }

    @Test
    void legacyPoliciesAreRestrictedBeforeTheNewConsolePolicyIsPublished() {
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        StorageAccessProperties settings = new StorageAccessProperties(
                true, endpoint, "lake", "http://console", Duration.ofSeconds(2), List.of("readonly", "readwrite"));
        RustFsPolicies migrating = new RustFsPolicies(settings, properties, access, specs,
                JsonMapper.builder().build(), HttpClient.newHttpClient(), Clock.fixed(NOW, ZoneOffset.UTC));

        migrating.apply();

        assertThat(names).containsExactly("name=readonly", "name=readwrite", "name=dataflow_datasets");
        assertThatThrownBy(() -> new StorageAccessProperties(
                true, endpoint, "lake", "http://console", Duration.ofSeconds(2), List.of("consoleAdmin")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
