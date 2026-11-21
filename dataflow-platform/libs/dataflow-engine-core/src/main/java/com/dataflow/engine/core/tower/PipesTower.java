package com.dataflow.engine.core.tower;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class PipesTower implements TowerReporter {

    public static final String CONTEXT_ENV = "DAGSTER_PIPES_CONTEXT";
    public static final String MESSAGES_ENV = "DAGSTER_PIPES_MESSAGES";

    private static final String VERSION = "0.1";
    private static final String PATH = "path";
    private static final int BUFFER = 8192;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonNode context;
    private final PrintStream out;

    PipesTower(JsonNode context, PrintStream out) {
        this.context = context;
        this.out = out;
        emit("opened", Map.of("extras", Map.of()));
    }

    public static TowerReporter fromEnvironment(Map<String, String> env) {
        if (!env.containsKey(CONTEXT_ENV)) {
            return new NoTower();
        }
        JsonNode messages = decode(env.get(MESSAGES_ENV));
        return new PipesTower(context(decode(env.get(CONTEXT_ENV))), target(messages));
    }

    static JsonNode context(JsonNode params) {
        if (params.has("data")) {
            return params.get("data");
        }
        if (params.has(PATH)) {
            try {
                return JSON.readTree(new File(params.get(PATH).asText()));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read the Dagster Pipes context", e);
            }
        }
        return params;
    }

    static Map<String, Object> metadata(Map<String, ?> values) {
        Map<String, Object> typed = new LinkedHashMap<>();
        values.forEach((label, value) -> {
            if (value != null) {
                typed.put(label, Map.of("raw_value", value, "type", type(value)));
            }
        });
        return typed;
    }

    private static String type(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return "int";
        }
        if (value instanceof Number) {
            return "float";
        }
        if (value instanceof Boolean) {
            return "bool";
        }
        if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return "json";
        }
        return "text";
    }

    private static PrintStream target(JsonNode messages) {
        if (messages.has(PATH)) {
            try {
                OutputStream file = new FileOutputStream(messages.get(PATH).asText(), true);
                return new PrintStream(file, true, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return "stderr".equals(messages.path("stdio").asText()) ? System.err : System.out;
    }

    static JsonNode decode(String raw) {
        byte[] compressed = Base64.getDecoder().decode(raw);
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER];
        try {
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0 && inflater.needsInput()) {
                    break;
                }
                json.write(buffer, 0, read);
            }
            return JSON.readTree(json.toByteArray());
        } catch (DataFormatException | IOException e) {
            throw new IllegalArgumentException("Cannot decode a Dagster Pipes parameter", e);
        } finally {
            inflater.end();
        }
    }

    private static Optional<String> text(JsonNode node) {
        return node == null || node.isNull() ? Optional.empty() : Optional.of(node.asText());
    }

    @Override
    public Optional<String> runId() {
        return text(context.get("run_id"));
    }

    @Override
    public Optional<String> partitionKey() {
        return text(context.get("partition_key"));
    }

    @Override
    public void materialized(String assetKey, Map<String, ?> metadata) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("asset_key", assetKey);
        params.put("data_version", null);
        params.put("metadata", metadata(metadata));
        emit("report_asset_materialization", params);
    }

    @Override
    public void checked(String assetKey, String check, boolean passed, Severity severity, Map<String, ?> metadata) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("asset_key", assetKey);
        params.put("check_name", check);
        params.put("passed", passed);
        params.put("severity", severity.name());
        params.put("metadata", metadata(metadata));
        emit("report_asset_check", params);
    }

    @Override
    public void log(String level, String message) {
        emit("log", Map.of("level", level, "message", message));
    }

    @Override
    public void close() {
        emit("closed", Map.of());
        out.flush();
    }

    private synchronized void emit(String method, Map<String, ?> params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("__dagster_pipes_version", VERSION);
        message.put("method", method);
        message.put("params", params);
        try {
            out.println(JSON.writeValueAsString(message));
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
