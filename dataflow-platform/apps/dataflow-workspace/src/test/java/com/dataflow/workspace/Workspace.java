package com.dataflow.workspace;

import com.dataflow.spec.ReleaseId;
import com.dataflow.workspace.config.HttpProperties;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.releases.DeployedView;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.util.unit.DataSize;

public final class Workspace {

    public static final Path PIPELINES = Path.of("src/test/resources/pipelines");
    public static final HttpProperties HTTP = new HttpProperties(Duration.ofSeconds(5), Duration.ofSeconds(5));
    public static final WorkspaceProperties.Auth AUTH_OFF = new WorkspaceProperties.Auth(
            WorkspaceProperties.AuthMode.NONE,
            "",
            "",
            "workspace",
            "",
            "groups",
            "preferred_username",
            List.of("admins"));
    public static final WorkspaceProperties.GitHub GITHUB =
            new WorkspaceProperties.GitHub("https://github.test", "https://api.github.test", "", "", "", "", Duration.ofSeconds(15));

    private Workspace() {}

    public static DeployedView deployed() {
        return deployed(PIPELINES);
    }

    public static DeployedView deployed(Path root) {
        return new DeployedView() {
            @Override
            public Path root() {
                return root;
            }

            @Override
            public Optional<ReleaseId> releaseOf(String dataset) {
                return Optional.empty();
            }
        };
    }

    public static WorkspaceProperties properties() {
        return properties(Path.of("target/test-git"));
    }

    public static WorkspaceProperties properties(Path gitCache) {
        return new WorkspaceProperties(
                new WorkspaceProperties.Person("test", "Test", "test@dataflow.local"),
                AUTH_OFF,
                new WorkspaceProperties.Git(gitCache.toString(), List.of(), GITHUB),
                new WorkspaceProperties.Releases(
                        "releases",
                        "",
                        "",
                        "",
                        "prod",
                        "target/test-releases",
                        Duration.ofMinutes(1),
                        "us-east-1",
                        true),
                new WorkspaceProperties.Serving(
                        "http://serving.test:8123",
                        "test",
                        "",
                        "serving",
                        "lake",
                        "s3a://lake",
                        100,
                        100,
                        Duration.ofSeconds(5)),
                new WorkspaceProperties.Tower(
                        "http://tower.test:3000/graphql", "dataflow", "__repository__", "engine_task",
                        Duration.ofSeconds(30),
                        DataSize.ofMegabytes(4)),
                new WorkspaceProperties.Catalog(Duration.ofMinutes(2), DataSize.ofMegabytes(32)),
                new WorkspaceProperties.Links("", "", "", "", "", ""),
                new WorkspaceProperties.Alerts("", "", Duration.ofSeconds(10)),
                List.of());
    }
}
