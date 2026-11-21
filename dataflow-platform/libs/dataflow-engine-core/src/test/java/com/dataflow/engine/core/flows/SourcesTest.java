package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.run.HttpTimeouts;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourcesTest {

    private static final int NO_BODY = -1;

    @TempDir
    Path cache;

    private HttpServer server;
    private String base;
    private Sources sources;

    private void redirect(String path, String location) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, NO_BODY);
            exchange.close();
        });
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/data.csv", exchange -> {
            byte[] body = "id\n1\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        sources = new Sources(cache, new HttpTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(5)));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aRedirectOnTheSameHostIsFollowed() throws IOException {
        redirect("/old.csv", base + "/data.csv");

        Path file = sources.download("task", base + "/old.csv");

        assertThat(Files.readString(file)).isEqualTo("id\n1\n");
    }

    @Test
    void aRelativeRedirectIsFollowed() throws IOException {
        redirect("/moved.csv", "/data.csv");

        assertThat(Files.readString(sources.download("task", base + "/moved.csv"))).isEqualTo("id\n1\n");
    }

    @Test
    void aRedirectToAnInternalAddressIsRefused() {
        redirect("/metadata.csv", "http://169.254.169.254/latest/meta-data/");
        redirect("/service.csv", "http://clickhouse:8123/?query=select+1");

        assertThatThrownBy(() -> sources.download("task", base + "/metadata.csv"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("internal address");
        assertThatThrownBy(() -> sources.download("task", base + "/service.csv"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("internal address");
    }

    @Test
    void aRedirectLoopStops() {
        redirect("/loop.csv", base + "/loop.csv");

        assertThatThrownBy(() -> sources.download("task", base + "/loop.csv"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too many redirects");
    }
}
