package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.run.HttpTimeouts;
import com.dataflow.spec.FileSource;
import com.dataflow.spec.InternalAddress;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

@Slf4j
public class Sources {

    private static final int MAX_REDIRECTS = 5;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private static final String HTTPS = "https";

    private final Path cacheDir;
    private final Duration timeout;
    private final HttpClient http;

    public Sources(Path cacheDir, HttpTimeouts timeouts) {
        this.cacheDir = cacheDir;
        this.timeout = timeouts.response();
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeouts.connect())
                .build();
    }

    static String fileName(URI url) {
        String path = url.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public Path fetch(String dataset, FileSource source, boolean reuseCached) {
        URI url = URI.create(source.url());
        Path target = cacheDir.resolve(dataset).resolve(fileName(url));
        if (reuseCached && Files.exists(target)) {
            log.info("Using cached {}", target);
            return target;
        }
        download(url, target);
        return target;
    }

    public Path download(String folder, String url) {
        URI uri = URI.create(url);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only http and https sources can be sampled: " + url);
        }
        Path target = cacheDir.resolve(folder).resolve(fileName(uri));
        download(uri, target);
        return target;
    }

    private HttpResponse<InputStream> follow(URI url) throws IOException, InterruptedException {
        URI at = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpResponse<InputStream> response = http.send(
                    HttpRequest.newBuilder(at).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (!REDIRECTS.contains(response.statusCode())) {
                return response;
            }
            response.body().close();
            at = next(url, at, response);
        }
        throw refused(url, " followed too many redirects");
    }

    private static URI next(URI origin, URI at, HttpResponse<InputStream> response) {
        URI next = at.resolve(response.headers()
                .firstValue("Location")
                .orElseThrow(() -> refused(at, " redirects nowhere")));
        boolean downgrade = HTTPS.equalsIgnoreCase(at.getScheme()) && !HTTPS.equalsIgnoreCase(next.getScheme());
        boolean web = HTTPS.equalsIgnoreCase(next.getScheme()) || "http".equalsIgnoreCase(next.getScheme());
        if (downgrade || !web) {
            throw refused(origin, " redirects to " + next + ", which is not read");
        }
        if (!origin.getHost().equalsIgnoreCase(next.getHost()) && InternalAddress.is(next.getHost())) {
            throw refused(origin, " redirects to the internal address " + next.getHost());
        }
        return next;
    }

    private static IllegalStateException refused(URI url, String why) {
        return new IllegalStateException("GET " + url + why);
    }

    private void download(URI url, Path target) {
        log.info("Downloading {} to {}", url, target);
        Path partial = target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID() + ".part");
        try {
            Files.createDirectories(target.getParent());
            HttpResponse<InputStream> response = follow(url);
            if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                response.body().close();
                throw refused(url, " returned HTTP " + response.statusCode());
            }
            try (InputStream body = response.body()) {
                Files.copy(body, partial, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading " + url, e);
        } finally {
            try {
                Files.deleteIfExists(partial);
            } catch (IOException ignored) {
                log.debug("Could not remove {}", partial);
            }
        }
    }
}
