package com.dataflow.engine.core.tower;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import static org.assertj.core.api.Assertions.assertThat;

class PipesTowerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;

    private static JsonNode parse(String line) {
        try {
            return JSON.readTree(line);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String json) {
        Deflater deflater = new Deflater();
        deflater.setInput(json.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void withoutPipesContext_thereIsNoTower() {
        assertThat(PipesTower.fromEnvironment(Map.of())).isInstanceOf(NoTower.class);
    }

    @Test
    void readsTheContextFromAFileWhenGivenAPath() throws Exception {
        Path context = dir.resolve("context.json");
        Files.writeString(context, "{\"run_id\":\"r-2\"}");

        assertThat(PipesTower.context(JSON.readTree("{\"path\":\"" + context + "\"}"))
                        .get("run_id")
                        .asText())
                .isEqualTo("r-2");
    }

    @Test
    void writesThePipesProtocolAsJsonLines() throws Exception {
        Path messages = dir.resolve("messages.jsonl");
        Map<String, String> env = Map.of(
                PipesTower.CONTEXT_ENV,
                encode("{\"data\":{\"run_id\":\"r-1\",\"partition_key\":\"2024-01\",\"asset_keys\":[\"a/b\"]}}"),
                PipesTower.MESSAGES_ENV,
                encode("{\"path\":\"" + messages + "\"}"));

        try (TowerReporter tower = PipesTower.fromEnvironment(env)) {
            assertThat(tower.runId()).contains("r-1");
            assertThat(tower.partitionKey()).contains("2024-01");
            tower.materialized("a/b", Map.of("rows", 42L, "table", "s3a://lake/b/"));
            tower.checked("a/b", "positive", false, Severity.WARN, Map.of("failing_rows", 3L));
        }

        List<JsonNode> lines =
                Files.readAllLines(messages).stream().map(PipesTowerTest::parse).toList();
        assertThat(lines)
                .extracting(line -> line.get("method").asText())
                .containsExactly("opened", "report_asset_materialization", "report_asset_check", "closed");
        assertThat(lines)
                .allSatisfy(line ->
                        assertThat(line.get("__dagster_pipes_version").asText()).isEqualTo("0.1"));
        JsonNode materialization = lines.get(1).get("params");
        assertThat(materialization.get("asset_key").asText()).isEqualTo("a/b");
        assertThat(materialization.at("/metadata/rows/type").asText()).isEqualTo("int");
        assertThat(materialization.at("/metadata/table/type").asText()).isEqualTo("text");
        JsonNode check = lines.get(2).get("params");
        assertThat(check.get("passed").asBoolean()).isFalse();
        assertThat(check.get("severity").asText()).isEqualTo("WARN");
    }
}
