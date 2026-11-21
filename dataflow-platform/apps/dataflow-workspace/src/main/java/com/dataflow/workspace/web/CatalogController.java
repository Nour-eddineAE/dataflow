package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Visibility;
import com.dataflow.workspace.catalog.CatalogTree;
import com.dataflow.workspace.catalog.CatalogViews;
import com.dataflow.workspace.catalog.TableService;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.SchemaColumn;
import com.dataflow.workspace.setup.PlatformServices;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final TableService tables;
    private final CatalogViews views;
    private final Visibility visibility;
    private final ClickHouse serving;
    private final PlatformServices services;

    public CatalogController(
            TableService tables, CatalogViews views, Visibility visibility, ClickHouse serving, PlatformServices services) {
        this.tables = tables;
        this.views = views;
        this.visibility = visibility;
        this.serving = serving;
        this.services = services;
    }

    @GetMapping("/catalog")
    public CatalogTree catalog() {
        return visibility.catalog(tables.tree());
    }

    @GetMapping("/catalog/schema")
    public Map<String, List<SchemaColumn>> schema() {
        return visibility.schema(tables.schema(), tables.tree(), serving.database());
    }

    @PostMapping("/catalog/sync")
    public Synced sync() {
        services.requireManage(PlatformServices.SERVING);
        return new Synced(views.sync(true));
    }

    public record Synced(int views) {}
}
