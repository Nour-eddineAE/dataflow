package com.dataflow.workspace.folders;

import com.dataflow.workspace.config.WorkspaceProperties;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class GitHub {

    public static final String PROVIDER = "github";
    private static final String API_JSON = "application/vnd.github+json";
    private static final String CLIENT_ID = "client_id";
    private static final String REFRESH_TOKEN = "refresh_token";

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{1,200}");

    private final WorkspaceProperties.GitHub settings;
    private final HttpClient http;
    private final ObjectMapper json;
    private final GitHubAppStore apps;
    private final StringEncryptor encryptor;

    public GitHub(
            WorkspaceProperties properties,
            HttpClient http,
            ObjectMapper json,
            GitHubAppStore apps,
            StringEncryptor encryptor) {
        this.settings = properties.git().github();
        this.http = http;
        this.json = json;
        this.apps = apps;
        this.encryptor = encryptor;
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String strip(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public WorkspaceProperties.GitHub settings() {
        return settings;
    }

    public Optional<Client> client() {
        if (settings.enabled()) {
            return Optional.of(new Client(settings.clientId(), settings.clientSecret(), settings.appSlug()));
        }
        return apps.find(host())
                .map(app -> new Client(app.clientId(), encryptor.decrypt(app.clientSecret()), app.slug()));
    }

    public URI newApp(String state) {
        return URI.create(host() + "/settings/apps/new?state=" + URLEncoder.encode(state, StandardCharsets.UTF_8));
    }

    public String manifest(String base, String name) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", name);
        manifest.put("url", base);
        manifest.put("redirect_url", base + GitHubLinks.CREATED);
        manifest.put("callback_urls", List.of(base + GitHubLinks.CALLBACK));
        manifest.put("setup_url", base + GitHubLinks.INSTALLED);
        manifest.put("setup_on_update", true);
        manifest.put("public", false);
        manifest.put("default_permissions", Map.of("contents", "write", "metadata", "read"));
        manifest.put("default_events", List.of());
        try {
            return json.writeValueAsString(manifest);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    public GitHubApp convert(String code, Person person, Instant now) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("GitHub sent no usable code");
        }
        JsonNode created = send(api("/app-manifests/" + code + "/conversions").POST(HttpRequest.BodyPublishers.noBody()));
        GitHubApp app = new GitHubApp(
                host(),
                created.path("id").asLong(),
                created.path("slug").asString(),
                created.path(CLIENT_ID).asString(),
                encryptor.encrypt(created.path("client_secret").asString()),
                encryptor.encrypt(created.path("pem").asString()),
                person.id(),
                now);
        if (app.slug().isBlank() || app.clientId().isBlank()) {
            throw new GitFolderException("GitHub did not return the new app");
        }
        apps.save(app);
        return app;
    }

    public URI install(String slug) {
        return URI.create(host() + "/apps/" + URLEncoder.encode(slug, StandardCharsets.UTF_8) + "/installations/new");
    }

    public long installations(String accessToken) {
        return send(api("/user/installations", accessToken).GET())
                .path("total_count")
                .asLong();
    }

    public URI authorize(String state, String redirectUri) {
        Client client = required();
        Map<String, String> query = new LinkedHashMap<>();
        query.put(CLIENT_ID, client.clientId());
        query.put("redirect_uri", redirectUri);
        query.put("scope", "repo");
        query.put("state", state);
        return URI.create(host() + "/login/oauth/authorize?" + form(query));
    }

    public Token exchange(String code, String redirectUri) {
        Map<String, String> body = signedByClient();
        body.put("code", code);
        body.put("redirect_uri", redirectUri);
        return token(body);
    }

    public Token refresh(String refreshToken) {
        Map<String, String> body = signedByClient();
        body.put("grant_type", REFRESH_TOKEN);
        body.put(REFRESH_TOKEN, refreshToken);
        return token(body);
    }

    public String login(String accessToken) {
        JsonNode user = send(api("/user", accessToken).GET());
        String login = user.path("login").asString("");
        if (login.isBlank()) {
            throw new GitFolderException("GitHub did not say who the token belongs to");
        }
        return login;
    }

    private HttpRequest.Builder api(String path) {
        return HttpRequest.newBuilder(URI.create(strip(settings.api()) + path)).header(HttpHeaders.ACCEPT, API_JSON);
    }

    private HttpRequest.Builder api(String path, String accessToken) {
        return api(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    private Map<String, String> signedByClient() {
        Client client = required();
        Map<String, String> body = new LinkedHashMap<>();
        body.put(CLIENT_ID, client.clientId());
        body.put("client_secret", client.clientSecret());
        return body;
    }

    private Token token(Map<String, String> body) {
        JsonNode answer = send(HttpRequest.newBuilder(URI.create(host() + "/login/oauth/access_token"))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(form(body))));
        if (answer.hasNonNull("error")) {
            throw new GitFolderException("GitHub refused: "
                    + answer.path("error_description")
                            .asString(answer.path("error").asString()));
        }
        String access = answer.path("access_token").asString("");
        if (access.isBlank()) {
            throw new GitFolderException("GitHub returned no token");
        }
        String refresh = answer.path(REFRESH_TOKEN).asString("");
        return new Token(
                access,
                refresh.isBlank() ? null : refresh,
                answer.hasNonNull("expires_in") ? answer.path("expires_in").asLong() : null);
    }

    private JsonNode send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response =
                    http.send(request.timeout(settings.timeout()).build(), HttpResponse.BodyHandlers.ofString());
            if (HttpStatusCode.valueOf(response.statusCode()).isError()) {
                throw new GitFolderException("GitHub answered HTTP " + response.statusCode());
            }
            return json.readTree(response.body());
        } catch (IOException | JacksonException e) {
            throw new GitFolderException("GitHub could not be reached: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitFolderException("Interrupted while calling GitHub");
        }
    }

    private Client required() {
        return client().orElseThrow(() -> new GitFolderException("No GitHub App yet; link GitHub again to create one"));
    }

    private String host() {
        return strip(settings.host());
    }

    public record Token(String accessToken, String refreshToken, Long expiresIn) {}

    public record Client(String clientId, String clientSecret, String slug) {}
}
