package com.dataflow.workspace.setup;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.config.WorkspaceConfiguration;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.releases.InMemoryReleaseStore;
import com.dataflow.workspace.releases.ReleaseStore;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SetupChecksTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);
    private static final String ISSUER = "https://dataflow.example.com/auth/realms/dataflow";

    private HttpServer provider;

    private static SetupChecks checks(
            ClickHouse serving, ReleaseStore releases, Tower tower, WorkspaceProperties properties) {
        return new SetupChecks(
                serving,
                new LakeFunctions(properties),
                tower,
                releases,
                properties,
                CLOCK,
                new SignInCheck(HttpClient.newHttpClient(), JSON, properties, Workspace.HTTP));
    }

    private static Tower tower(boolean up) {
        return new Tower(Workspace.properties(), HttpClient.newHttpClient(), JSON) {
            @Override
            public boolean reachable() {
                return up;
            }
        };
    }

    private static WorkspaceProperties signIn(String issuer, String internal) {
        WorkspaceProperties base = Workspace.properties();
        WorkspaceProperties.Auth auth = new WorkspaceProperties.Auth(
                WorkspaceProperties.AuthMode.OIDC,
                issuer,
                internal,
                "workspace",
                "",
                "groups",
                "preferred_username",
                List.of("admins"));
        return new WorkspaceProperties(
                base.person(),
                auth,
                base.git(),
                base.releases(),
                base.serving(),
                base.tower(),
                base.catalog(),
                base.links(),
                base.alerts(),
                base.dashboards());
    }

    @AfterEach
    void stop() {
        if (provider != null) {
            provider.stop(0);
        }
    }

    @Test
    void aHealthyInstall_passesEveryCheck() {
        InMemoryReleaseStore releases = new InMemoryReleaseStore();

        List<Check> checks = checks(new RecordingClickHouse(), releases, tower(true), Workspace.properties())
                .run();

        assertThat(checks)
                .extracting(Check::name)
                .containsExactly("ClickHouse", "Lake", "Releases", "Tower", "Sign-in");
        assertThat(checks).allMatch(Check::ok);
        assertThat(checks.getLast().detail()).isEqualTo("Off: everyone is an administrator");
        assertThat(new String(releases.objects.get(SetupChecks.RELEASES_PROBE), StandardCharsets.UTF_8))
                .isEqualTo("2026-10-28T12:00:00Z");
    }

    @Test
    void anUnreachableClickHouse_explainsWhatStopsAndSkipsTheLake() {
        ClickHouse down =
                new ClickHouse(
                        Workspace.properties(),
                        HttpClient.newHttpClient(),
                        JSON,
                        new WorkspaceConfiguration().clickHouseSql()) {
                    @Override
                    public boolean reachable() {
                        return false;
                    }
                };

        List<Check> checks = checks(down, new InMemoryReleaseStore(), tower(true), Workspace.properties())
                .run();

        assertThat(checks.get(0))
                .isEqualTo(new Check(
                        "ClickHouse",
                        false,
                        "No answer at http://serving.test:8123. Queries, the catalog and dashboards need it."));
        assertThat(checks.get(1)).isEqualTo(new Check("Lake", false, "Not checked: ClickHouse reads the lake"));
    }

    @Test
    void aReleaseStoreThatRefusesWrites_namesTheBucketAndTheReason() {
        ReleaseStore refusing = new ReleaseStore() {
            @Override
            public void put(String key, byte[] content) {
                throw new IllegalStateException("Access Denied (Service: S3, Status Code: 403)\nrequest id 1");
            }

            @Override
            public Optional<byte[]> get(String key) {
                return Optional.empty();
            }

            @Override
            public List<String> projects() {
                return List.of();
            }

            @Override
            public List<StoredObject> list(String prefix) {
                return List.of();
            }

            @Override
            public void delete(List<String> keys) {}
        };

        Check releases = checks(new RecordingClickHouse(), refusing, tower(false), Workspace.properties())
                .releases();

        assertThat(releases)
                .isEqualTo(new Check(
                        "Releases",
                        false,
                        "Cannot write bucket releases: Access Denied (Service: S3, Status Code: 403)"));
    }

    @Test
    void theSignInProvider_mustSignTokensWithTheExpectedIssuer() throws IOException {
        String internal = serve(ISSUER);
        assertThat(checks(signIn(ISSUER, internal)).signIn())
                .isEqualTo(new Check("Sign-in", true, "Provider " + ISSUER));

        String other = "http://keycloak:8080/realms/dataflow";
        assertThat(checks(signIn(other, internal)).signIn())
                .isEqualTo(new Check(
                        "Sign-in",
                        false,
                        "The provider signs tokens as " + ISSUER + " but the Workspace expects " + other));
    }

    private SetupChecks checks(WorkspaceProperties properties) {
        return checks(new RecordingClickHouse(), new InMemoryReleaseStore(), tower(true), properties);
    }

    private String serve(String issuer) throws IOException {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/realms/dataflow/.well-known/openid-configuration", exchange -> {
            byte[] body = ("{\"issuer\":\"" + issuer + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        provider.start();
        return "http://127.0.0.1:" + provider.getAddress().getPort() + "/realms/dataflow";
    }
}
