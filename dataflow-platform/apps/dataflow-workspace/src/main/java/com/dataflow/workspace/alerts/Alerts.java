package com.dataflow.workspace.alerts;

import com.dataflow.workspace.config.WorkspaceProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class Alerts {

    private static final String PATH = "/api/prometheus/grafana/api/v1/alerts";

    private final WorkspaceProperties.Alerts settings;
    private final HttpClient http;
    private final ObjectMapper json;

    public Alerts(WorkspaceProperties properties, HttpClient http, ObjectMapper json) {
        this.settings = properties.alerts();
        this.http = http;
        this.json = json;
    }

    static List<Alert> parse(JsonNode body) {
        List<Alert> alerts = new ArrayList<>();
        for (JsonNode alert : body.path("data").path("alerts")) {
            String state = alert.path("state").asString().toLowerCase(Locale.ROOT);
            if (state.startsWith("normal") || state.startsWith("inactive")) {
                continue;
            }
            JsonNode labels = alert.path("labels");
            alerts.add(new Alert(
                    labels.path("alertname").asString(),
                    text(labels.path("dataset")),
                    text(labels.path("severity")),
                    state,
                    text(alert.path("activeAt")),
                    text(alert.path("annotations").path("summary"))));
        }
        return alerts;
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() || node.asString().isBlank() ? null : node.asString();
    }

    private static String strip(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public Current current() {
        if (!settings.enabled()) {
            return new Current(false, "Grafana is not configured", List.of());
        }
        String token = token();
        if (token.isBlank()) {
            return new Current(false, "No Grafana token yet; start the monitoring stack", List.of());
        }
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(strip(settings.grafana()) + PATH))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                            .timeout(settings.timeout())
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpStatus.OK.value()) {
                return new Current(false, "Grafana answered HTTP " + response.statusCode(), List.of());
            }
            return new Current(true, null, parse(json.readTree(response.body())));
        } catch (IOException | JacksonException e) {
            return new Current(false, "Grafana is not reachable", List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Current(false, "Interrupted", List.of());
        }
    }

    private String token() {
        if (settings.tokenFile() == null || settings.tokenFile().isBlank()) {
            return "";
        }
        try {
            Path file = Path.of(settings.tokenFile());
            return Files.isRegularFile(file) ? Files.readString(file).strip() : "";
        } catch (IOException e) {
            return "";
        }
    }

    public record Current(boolean available, String reason, List<Alert> alerts) {}
}
