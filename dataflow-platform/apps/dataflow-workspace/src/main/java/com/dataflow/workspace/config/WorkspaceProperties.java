package com.dataflow.workspace.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("dataflow.workspace")
@Validated
public record WorkspaceProperties(
        @Valid @NotNull Person person,
        @Valid @NotNull Auth auth,
        @Valid @NotNull Git git,
        @Valid @NotNull Releases releases,
        @Valid @NotNull Serving serving,
        @Valid @NotNull Tower tower,
        @Valid @NotNull Catalog catalog,
        @Valid @NotNull Links links,
        @Valid @NotNull Alerts alerts,
        @NotNull List<@Valid Dashboard> dashboards) {

    public static final String SYNC_EVERY = "${dataflow.workspace.catalog.sync-every}";

    public enum AuthMode {
        NONE,
        OIDC
    }

    public record Person(
            @NotBlank String id,
            @NotBlank String name,
            @NotBlank String email) {}

    public record Auth(
            @NotNull AuthMode mode,
            String issuer,
            String internal,
            String clientId,
            String clientSecret,
            @NotBlank String groupsClaim,
            @NotBlank String usernameClaim,
            @NotNull List<String> admins) {

        public boolean enabled() {
            return mode == AuthMode.OIDC;
        }
    }

    public record Git(
            @NotBlank String cacheDir,
            @NotNull List<String> fileRemotes,
            @Valid @NotNull GitHub github) {}

    public record GitHub(
            @NotBlank String host,
            @NotBlank String api,
            String clientId,
            String clientSecret,
            String appSlug,
            String publicUrl,
            @NotNull Duration timeout) {

        public boolean enabled() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public record Releases(
            @NotBlank String bucket,
            String endpoint,
            String accessKey,
            String secretKey,
            @NotBlank String target,
            @NotBlank String cacheDir,
            @NotNull Duration refreshEvery,
            @NotBlank String region,
            boolean pathStyle) {}

    public record Serving(
            @NotBlank String url,
            @NotBlank String user,
            @NotNull String password,
            @NotBlank String database,
            @NotBlank String collection,
            @NotBlank String lakeRoot,
            @Min(1) int maxRows,
            @Min(1) int dashboardRows,
            @NotNull Duration timeout) {}

    public record Tower(
            @NotBlank String url,
            @NotBlank String location,
            @NotBlank String repository,
            @NotBlank String taskJob,
            @NotNull Duration timeout,
            @NotNull DataSize maxOutput) {}

    public record Catalog(@NotNull Duration syncEvery, @NotNull DataSize smallFile) {}

    public record Links(String tower, String objectStore, String serving, String dashboards, String mail, String sparkHistory) {}

    public record Alerts(String grafana, String tokenFile, @NotNull Duration timeout) {

        public boolean enabled() {
            return grafana != null && !grafana.isBlank();
        }
    }

    public record Dashboard(
            @NotBlank String uid,
            @NotBlank String title,
            @NotBlank String group,
            String description,
            String datasetVariable) {}
}
