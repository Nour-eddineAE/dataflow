package com.dataflow.workspace.preflight;

import com.dataflow.spec.FileSource;
import com.dataflow.workspace.config.HttpProperties;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

import static org.jooq.impl.DSL.selectCount;

@Component
public class SourceProbe {

    private static final Set<Integer> HEAD_REFUSED = Set.of(403, 405, 501);
    private static final Set<String> WEB = Set.of("http", "https");

    private final HttpClient http;
    private final Duration timeout;
    private final ClickHouse serving;
    private final LakeFunctions lake;

    public SourceProbe(HttpClient http, HttpProperties settings, ClickHouse serving, LakeFunctions lake) {
        this.http = http;
        this.timeout = settings.probeTimeout();
        this.serving = serving;
        this.lake = lake;
    }

    public Finding probe(String subject, FileSource source) {
        return source.url() != null ? web(subject, source.url()) : lake(subject, source.path());
    }

    private Finding web(String subject, String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Finding.failed(subject, "Not a valid address");
        }
        if (!WEB.contains(uri.getScheme())) {
            return Finding.failed(subject, "Only http and https addresses are read");
        }
        try {
            int status = status(uri, "HEAD");
            if (HEAD_REFUSED.contains(status)) {
                status = status(uri, "GET");
            }
            return !HttpStatusCode.valueOf(status).isError()
                    ? Finding.passed(subject, uri.getHost() + " answers (HTTP " + status + ")")
                    : Finding.failed(subject, uri.getHost() + " answers HTTP " + status);
        } catch (IOException e) {
            return Finding.failed(subject, "No answer from " + uri.getHost());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Finding.failed(subject, "Interrupted");
        }
    }

    private int status(URI uri, String method) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Range", "bytes=0-0")
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private Finding lake(String subject, String path) {
        long files = serving.fetchValue(selectCount().from(lake.objects(path)), Integer.class);
        return files > 0
                ? Finding.passed(subject, files + (files == 1 ? " file matches " : " files match ") + path)
                : Finding.warning(subject, "No file matches " + path + " yet: a run would find nothing to ingest");
    }
}
