package com.dataflow.workspace.preflight;

import com.dataflow.spec.ReferenceSpec;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.preflight.Finding.Verdict;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceProbeTest {

    private final RecordingClickHouse serving = new RecordingClickHouse();
    private final SourceProbe probe = new SourceProbe(HttpClient.newHttpClient(), Workspace.HTTP, serving, new LakeFunctions(Workspace.properties()));
    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/feed.csv", exchange -> {
            exchange.sendResponseHeaders("HEAD".equals(exchange.getRequestMethod()) ? 405 : 206, -1);
            exchange.close();
        });
        server.createContext("/gone.csv", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aWebSource_isReachedWithGetWhenHeadIsRefused() {
        assertThat(probe.probe("Source", web(base + "/feed.csv")))
                .isEqualTo(new Finding("Source", Verdict.PASSED, "127.0.0.1 answers (HTTP 206)"));
    }

    @Test
    void aMissingOrUnreachableSource_fails_withoutEchoingTheBody() {
        assertThat(probe.probe("Source", web(base + "/gone.csv")).detail()).isEqualTo("127.0.0.1 answers HTTP 404");
        server.stop(0);
        assertThat(probe.probe("Source", web(base + "/feed.csv")).detail()).isEqualTo("No answer from 127.0.0.1");
        assertThat(probe.probe("Source", web("ftp://example.org/x.csv")).detail()).isEqualTo("Only http and https addresses are read");
    }

    @Test
    void aLakeGlob_isCountedThroughClickHouse() {
        serving.answer(sql -> sql.contains("inbox/sensors/"), List.of("count"), List.of(List.of(0)));

        Finding finding = probe.probe("Source", new ReferenceSpec("sensors", null, "inbox/sensors/*.csv", "csv", null));

        assertThat(finding.verdict()).isEqualTo(Verdict.WARNING);
        assertThat(finding.detail()).startsWith("No file matches inbox/sensors/*.csv yet");
    }

    private static ReferenceSpec web(String url) {
        return new ReferenceSpec("source", url, null, "csv", null);
    }
}
