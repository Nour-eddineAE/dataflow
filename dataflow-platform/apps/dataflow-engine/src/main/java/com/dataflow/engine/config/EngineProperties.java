package com.dataflow.engine.config;

import com.dataflow.engine.core.run.HttpTimeouts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("dataflow.engine")
@Validated
public record EngineProperties(
        @NotBlank String pipelinesLocation,
        @NotBlank String releasesRoot,
        @NotBlank String lakeRoot,
        @NotBlank String cacheDir,
        @Valid @NotNull Batching batching,
        @Valid @NotNull Sources sources,
        @Valid @NotNull Maintenance maintenance,
        @Valid @NotNull Serving serving,
        @Valid @NotNull Tasks tasks) {

    public record Batching(
            @Min(1) long maxRecordsPerFile, @Min(1) int maxFilesPerBatch) {}

    public record Sources(@NotNull Duration timeout, @NotNull Duration connectTimeout) {

        public HttpTimeouts timeouts() {
            return new HttpTimeouts(connectTimeout, timeout);
        }
    }

    public record Maintenance(
            boolean enabled,
            @NotNull Duration interval,
            @Min(0) int retentionHours) {}

    public record Tasks(@Min(1) int sampleRows, String draft, String transformations, String clean) {}

    public record Serving(
            boolean enabled,
            String url,
            String user,
            String password,
            @NotBlank String database,
            @NotBlank String collection,
            @NotBlank String refreshEvery,
            @NotNull Duration timeout,
            @NotNull Duration connectTimeout) {

        public HttpTimeouts timeouts() {
            return new HttpTimeouts(connectTimeout, timeout);
        }
    }
}
