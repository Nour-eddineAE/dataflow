package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.config.StorageAccessProperties;
import com.dataflow.workspace.folders.FolderStore;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.serving.ClickHouse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class OverviewController {

    private static final String UNKNOWN_VERSION = "development";

    private final Tower tower;
    private final ClickHouse serving;
    private final FolderStore folders;
    private final WorkspaceProperties.Links links;
    private final List<WorkspaceProperties.Dashboard> dashboards;
    private final String version;
    private final boolean signIn;
    private final Access access;
    private final StorageAccessProperties storage;

    public OverviewController(
            Tower tower,
            ClickHouse serving,
            FolderStore folders,
            WorkspaceProperties properties,
            ObjectProvider<BuildProperties> build,
            Access access,
            StorageAccessProperties storage) {
        this.tower = tower;
        this.serving = serving;
        this.folders = folders;
        this.links = properties.links();
        this.signIn = properties.auth().enabled();
        this.dashboards = properties.dashboards();
        this.access = access;
        this.storage = storage;
        BuildProperties info = build.getIfAvailable();
        this.version = info == null ? UNKNOWN_VERSION : info.getVersion();
    }

    @GetMapping("/dashboards")
    public List<WorkspaceProperties.Dashboard> dashboards() {
        return access.caller().admin() ? dashboards : List.of();
    }

    @GetMapping("/status")
    public Status status() {
        return new Status(tower.reachable(), serving.reachable(), !folders.folders().isEmpty(), version, signIn,
                access.caller().admin() ? links : new WorkspaceProperties.Links(
                        null, storage.enabled() ? "/storage" : links.objectStore(), null, null, null, null));
    }

    public record Status(
            boolean tower,
            boolean serving,
            boolean git,
            String version,
            boolean signIn,
            WorkspaceProperties.Links links) {}
}
