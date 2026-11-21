package com.dataflow.workspace.runs;

import com.dataflow.spec.settings.RecordedSetting;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TowerTest {

    private final AtomicReference<String> answer = new AtomicReference<>();
    private final AtomicReference<String> request = new AtomicReference<>();
    private HttpServer server;
    private Tower tower;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        WorkspaceProperties properties = mock(WorkspaceProperties.class);
        when(properties.tower()).thenReturn(new WorkspaceProperties.Tower(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/graphql", "dataflow", "__repository__", "engine_task",
                Duration.ofSeconds(30),
                DataSize.ofMegabytes(4)));
        tower = new Tower(properties, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
                JsonMapper.builder().build());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void aDeadCodeServerIsUnreachableBeforeAndAfterTheFirstReload() throws IOException {
        answer.set(resource("code-server-dead.json"));
        assertThat(tower.reachesCode("engine_task", Map.of())).isFalse();
        assertThat(request.get()).contains("\"pipelineName\":\"engine_task\"", "\"repositoryLocationName\":\"dataflow\"");

        answer.set(resource("code-server-after-one-reload.json"));
        assertThat(tower.reachesCode("engine_task", Map.of())).isFalse();
    }

    @Test
    void aPlanOrALoadErrorProvesTheCodeServerAnswers() throws IOException {
        answer.set(resource("code-server.json"));
        assertThat(tower.reachesCode("engine_task", Map.of())).isTrue();

        answer.set(resource("code-server-load-error.json"));
        assertThat(tower.reachesCode("engine_task", Map.of())).isTrue();
    }

    @Test
    void aReloadSucceedsOnlyWhenTheLocationLoads() throws IOException {
        answer.set(resource("reload-failed.json"));
        assertThat(tower.reload()).isFalse();

        answer.set(resource("reload.json"));
        assertThat(tower.reload()).isTrue();
    }

    @Test
    void settingsAreTheTowerResourceFieldsWithTheirValueInEffect() throws IOException {
        answer.set(resource("settings.json"));

        assertThat(tower.settings()).containsExactly(
                new RecordedSetting("tower.target", "String", "The release target this tower runs", "prod"),
                new RecordedSetting("tower.freshness_hours", "Int", "Hours after which a scheduled table counts as stale", "12"),
                new RecordedSetting("tower.kubernetes.single_pod", "Bool", "", "false"));
        assertThat(request.get()).contains("\"resourceName\":\"tower_settings\"");
    }

    private String resource(String name) throws IOException {
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/runs/tower/" + name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
