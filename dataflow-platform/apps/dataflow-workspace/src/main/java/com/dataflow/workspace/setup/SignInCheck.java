package com.dataflow.workspace.setup;

import com.dataflow.workspace.config.HttpProperties;
import com.dataflow.workspace.config.WorkspaceProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class SignInCheck {

    private static final String SIGN_IN = "Sign-in";
    private static final String DISCOVERY = "/.well-known/openid-configuration";

    private final HttpClient http;
    private final ObjectMapper json;
    private final WorkspaceProperties.Auth auth;
    private final Duration probeTimeout;

    public SignInCheck(HttpClient http, ObjectMapper json, WorkspaceProperties properties, HttpProperties httpSettings) {
        this.http = http;
        this.json = json;
        this.auth = properties.auth();
        this.probeTimeout = httpSettings.probeTimeout();
    }

    private static String strip(String url) {
        return url == null ? "" : url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    Check run() {
        if (!auth.enabled()) {
            return Check.passed(SIGN_IN, "Off: everyone is an administrator");
        }
        String internal = strip(auth.internal());
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(internal + DISCOVERY))
                            .timeout(probeTimeout)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpStatus.OK.value()) {
                return Check.failed(SIGN_IN, internal + " answered " + response.statusCode());
            }
            JsonNode discovery = json.readTree(response.body());
            String issuer = discovery.path("issuer").asText("");
            return issuer.equals(strip(auth.issuer()))
                    ? Check.passed(SIGN_IN, "Provider " + issuer)
                    : Check.failed(
                            SIGN_IN,
                            "The provider signs tokens as " + issuer + " but the Workspace expects "
                                    + strip(auth.issuer()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Check.failed(SIGN_IN, "Interrupted");
        } catch (Exception e) {
            return Check.unreachable(SIGN_IN, internal, ": " + Check.firstLine(e));
        }
    }
}
