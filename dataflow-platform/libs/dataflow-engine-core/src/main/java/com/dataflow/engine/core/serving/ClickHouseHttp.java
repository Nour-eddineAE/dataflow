package com.dataflow.engine.core.serving;

import com.dataflow.engine.core.run.HttpTimeouts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class ClickHouseHttp implements Warehouse {

    private final URI endpoint;
    private final String user;
    private final String password;
    private final Duration timeout;
    private final HttpClient http;

    public ClickHouseHttp(String url, String user, String password, HttpTimeouts timeouts) {
        this.timeout = timeouts.response();
        this.http = HttpClient.newBuilder().connectTimeout(timeouts.connect()).build();
        this.endpoint = URI.create(url.endsWith("/") ? url : url + "/");
        this.user = user;
        this.password = password;
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse("").strip();
    }

    private static String basicAuth(String user, String password) {
        String credentials = (user == null ? "" : user) + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void execute(String sql) {
        send(sql);
    }

    @Override
    public List<List<String>> query(String sql) {
        String body = send(sql + " FORMAT TSV");
        return body.lines()
                .filter(line -> !line.isEmpty())
                .map(line -> Arrays.asList(line.split("\t", -1)))
                .toList();
    }

    private String send(String sql) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", basicAuth(user, password))
                .POST(HttpRequest.BodyPublishers.ofString(sql, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("ClickHouse returned HTTP " + response.statusCode() + " for "
                        + firstLine(sql) + ": " + firstLine(response.body()));
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot reach ClickHouse at " + endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling ClickHouse", e);
        }
    }
}
