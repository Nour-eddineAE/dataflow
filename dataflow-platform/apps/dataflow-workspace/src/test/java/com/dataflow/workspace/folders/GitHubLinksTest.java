package com.dataflow.workspace.folders;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitHubLinksTest {

    private static final Person ME = new Person("local", "Local", "local@dataflow.local");
    private static final Person SOMEONE_ELSE = new Person("other", "Other", "other@dataflow.local");
    private static final String TOKENS = "/login/oauth/access_token";

    private final InMemoryFolderStore store = new InMemoryFolderStore();
    private final InMemoryGitHubAppStore apps = new InMemoryGitHubAppStore();
    private final List<Map<String, String>> tokenRequests = new ArrayList<>();
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private String base;
    private long installations = 1;
    private GitCredentials credentials;
    private GitHubLinks links;
    private GitHubLinks fresh;

    private static URI redirect(GitHubLinks.Next next) {
        return ((GitHubLinks.Redirect) next).to();
    }

    private static Map<String, String> query(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().toSingleValueMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> URLDecoder.decode(entry.getValue(), StandardCharsets.UTF_8)));
    }

    private static Map<String, String> form(String body) {
        return Stream.of(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    private static void reply(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(TOKENS, exchange -> {
            Map<String, String> form =
                    form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            tokenRequests.add(form);
            String answer = "refresh_token".equals(form.get("grant_type"))
                    ? "{\"access_token\":\"ghu_new\",\"refresh_token\":\"ghr_new\",\"expires_in\":28800}"
                    : "good-code".equals(form.get("code"))
                            ? "{\"access_token\":\"ghu_first\",\"refresh_token\":\"ghr_first\",\"expires_in\":28800}"
                            : "{\"error\":\"bad_verification_code\",\"error_description\":\"The code passed is"
                                    + " incorrect or expired.\"}";
            reply(exchange, answer);
        });
        server.createContext(
                "/user/installations", exchange -> reply(exchange, "{\"total_count\":" + installations + "}"));
        server.createContext("/user", exchange -> reply(exchange, "{\"login\":\"octocat\"}"));
        server.createContext(
                "/app-manifests/one-time-code/conversions",
                exchange -> reply(
                        exchange,
                        "{\"id\":42,\"slug\":\"dataflow-k3x9qa\",\"client_id\":\"Iv23.made\",\"client_secret\":\"made-secret\",\"pem\":\"-----BEGIN"
                            + " RSA PRIVATE KEY-----\"}"));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T18:00:00Z"), ZoneOffset.UTC);
        GitHub github = github(new WorkspaceProperties.GitHub(
                base, base, "Iv1.client", "secret", "dataflow-dev", "http://localhost:3080/", Duration.ofSeconds(15)));
        credentials = new GitCredentials(store, new GitFixture.PlainEncryptor(), github, clock);
        links = new GitHubLinks(github, credentials, new InMemorySignInStates(), clock);
        GitHub unconfigured = github(new WorkspaceProperties.GitHub(base, base, "", "", "", "http://localhost:3080", Duration.ofSeconds(15)));
        fresh = new GitHubLinks(
                unconfigured,
                new GitCredentials(store, new GitFixture.PlainEncryptor(), unconfigured, clock),
                new InMemorySignInStates(),
                clock);
    }

    private GitHub github(WorkspaceProperties.GitHub settings) {
        WorkspaceProperties defaults = Workspace.properties();
        WorkspaceProperties properties = new WorkspaceProperties(
                defaults.person(),
                defaults.auth(),
                new WorkspaceProperties.Git(defaults.git().cacheDir(), List.of(), settings),
                defaults.releases(),
                defaults.serving(),
                defaults.tower(),
                defaults.catalog(),
                defaults.links(),
                defaults.alerts(),
                defaults.dashboards());
        return new GitHub(properties, HttpClient.newHttpClient(), json, apps, new GitFixture.PlainEncryptor());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void signingIn_storesAnEncryptedTokenNamedAfterTheGitHubLogin() {
        Map<String, String> authorize = query(redirect(links.start(ME, "http://ignored:8080")));

        GitCredential credential =
                links.finish(ME, authorize.get("state"), "good-code").credential();

        assertThat(authorize)
                .containsEntry("client_id", "Iv1.client")
                .containsEntry("redirect_uri", "http://localhost:3080/api/git/github/callback");
        assertThat(credential.name()).isEqualTo("GitHub octocat");
        assertThat(credential.username()).isEqualTo("octocat");
        assertThat(credential.provider()).isEqualTo(GitHub.PROVIDER);
        assertThat(tokenRequests.getFirst())
                .containsEntry("redirect_uri", "http://localhost:3080/api/git/github/callback")
                .containsEntry("client_secret", "secret");
        assertThat(store.secret(credential.id()).orElseThrow()).satisfies(secret -> {
            assertThat(secret.secret()).isEqualTo("enc:ghu_first");
            assertThat(secret.refreshSecret()).isEqualTo("enc:ghr_first");
            assertThat(secret.expiresAt()).isEqualTo(Instant.parse("2026-10-26T02:00:00Z"));
        });
        assertThat(links.settings().installUrl()).endsWith("/apps/dataflow-dev/installations/new");
    }

    @Test
    void aStateWorksOnceAndOnlyForWhoStartedIt() {
        String state = query(redirect(links.start(ME, "http://localhost:3080"))).get("state");

        assertThatThrownBy(() -> links.finish(SOMEONE_ELSE, state, "good-code"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> links.finish(ME, state, "good-code")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> links.finish(ME, "made-up", "good-code")).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.credentials(ME.id())).isEmpty();
    }

    @Test
    void aRefusedCode_saysWhy() {
        String state = query(redirect(links.start(ME, "http://localhost:3080"))).get("state");

        assertThatThrownBy(() -> links.finish(ME, state, "stale")).hasMessageContaining("incorrect or expired");
    }

    @Test
    void linkingAgain_replacesTheTokenOfTheSameCredential() {
        GitCredential first = links.finish(
                        ME, query(redirect(links.start(ME, "x"))).get("state"), "good-code")
                .credential();
        GitCredential second = links.finish(
                        ME, query(redirect(links.start(ME, "x"))).get("state"), "good-code")
                .credential();

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(store.credentials(ME.id())).hasSize(1);
    }

    @Test
    void anExpiringToken_isRefreshedBeforeGitUsesIt() {
        UUID id = UUID.randomUUID();
        store.insertCredential(
                new GitCredential(id, ME.id(), "GitHub octocat", "octocat", GitHub.PROVIDER, Instant.EPOCH),
                new StoredSecret("enc:ghu_old", "enc:ghr_old", Instant.parse("2026-10-25T18:02:00Z")));

        assertThat(credentials.token(id)).isEqualTo("ghu_new");
        assertThat(tokenRequests.getLast())
                .containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "ghr_old");
        assertThat(store.secret(id).orElseThrow().secret()).isEqualTo("enc:ghu_new");
        assertThat(credentials.token(id)).isEqualTo("ghu_new");
        assertThat(tokenRequests).hasSize(1);
    }

    @Test
    void withoutAnApp_theFirstClickCreatesOneThenInstallsThenAuthorizes() throws Exception {
        GitHubLinks.CreateApp create = (GitHubLinks.CreateApp) fresh.start(ME, "http://ignored");
        Map<String, Object> manifest = json.readValue(create.manifest(), Map.class);

        assertThat(create.action().toString()).startsWith(base + "/settings/apps/new?state=");
        assertThat(manifest)
                .containsEntry("redirect_url", "http://localhost:3080/api/git/github/app/created")
                .containsEntry("callback_urls", List.of("http://localhost:3080/api/git/github/callback"))
                .containsEntry("setup_url", "http://localhost:3080/api/git/github/installed")
                .containsEntry("public", false)
                .containsEntry("default_permissions", Map.of("contents", "write", "metadata", "read"));
        assertThat(manifest).doesNotContainKey("hook_attributes");

        URI install = fresh.created(ME, query(create.action()).get("state"), "one-time-code");

        assertThat(install.toString()).isEqualTo(base + "/apps/dataflow-k3x9qa/installations/new");
        assertThat(apps.find(base).orElseThrow()).satisfies(app -> {
            assertThat(app.clientId()).isEqualTo("Iv23.made");
            assertThat(app.clientSecret()).isEqualTo("enc:made-secret");
            assertThat(app.privateKey()).startsWith("enc:");
        });

        Map<String, String> authorize = query(fresh.installed(ME, "http://ignored"));
        assertThat(authorize).containsEntry("client_id", "Iv23.made");

        installations = 0;
        GitHubLinks.Linked notInstalled = fresh.finish(ME, authorize.get("state"), "good-code");
        assertThat(notInstalled.install()).isEqualTo(install);
        assertThat(tokenRequests.getLast()).containsEntry("client_secret", "made-secret");

        installations = 2;
        GitHubLinks.Linked linked =
                fresh.finish(ME, query(fresh.installed(ME, "x")).get("state"), "good-code");
        assertThat(linked.install()).isNull();
        assertThat(linked.credential().name()).isEqualTo("GitHub octocat");
    }

    @Test
    void anAppConversionNeedsTheStateThatStartedIt() {
        assertThatThrownBy(() -> fresh.created(ME, "made-up", "one-time-code"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(apps.find(base)).isEmpty();
    }
}
