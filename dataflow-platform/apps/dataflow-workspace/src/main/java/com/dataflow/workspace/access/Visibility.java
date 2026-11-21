package com.dataflow.workspace.access;

import com.dataflow.workspace.catalog.CatalogTree;
import com.dataflow.workspace.catalog.LakeTable;
import com.dataflow.workspace.catalog.ServedTable;
import com.dataflow.workspace.serving.SchemaColumn;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class Visibility {

    private final Access access;

    public Visibility(Access access) {
        this.access = access;
    }

    public CatalogTree catalog(CatalogTree tree) {
        Caller caller = access.caller();
        if (caller.admin()) {
            return tree;
        }
        List<CatalogTree.DatasetTables> datasets = tree.datasets().stream()
                .filter(dataset -> access.sees(caller, dataset.dataset()))
                .map(dataset -> new CatalogTree.DatasetTables(
                        dataset.dataset(),
                        dataset.valid(),
                        dataset.tables().stream()
                                .filter(table -> access.can(
                                        caller, Privilege.SELECT, Securable.table(dataset.dataset(), table.id())))
                                .toList()))
                .toList();
        return new CatalogTree(
                datasets,
                tree.serving().stream().filter(served -> reads(caller, served)).toList());
    }

    public Set<String> sqlNames(CatalogTree tree, String servingDatabase) {
        CatalogTree visible = catalog(tree);
        Set<String> names = visible.datasets().stream()
                .flatMap(dataset -> dataset.tables().stream().map(LakeTable::sqlName))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        visible.serving().forEach(served -> names.add(servingDatabase + "." + served.table()));
        return names;
    }

    public Map<String, List<SchemaColumn>> schema(
            Map<String, List<SchemaColumn>> schema, CatalogTree tree, String servingDatabase) {
        Caller caller = access.caller();
        if (caller.admin()) {
            return schema;
        }
        Set<String> allowed = catalog(tree).datasets().stream()
                .flatMap(dataset -> dataset.tables().stream().map(LakeTable::sqlName))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        catalog(tree).serving().forEach(served -> allowed.add(servingDatabase + "." + served.table()));
        Map<String, List<SchemaColumn>> visible = new LinkedHashMap<>();
        schema.forEach((table, columns) -> {
            if (allowed.contains(table)) {
                visible.put(table, columns);
            }
        });
        return visible;
    }

    public void requireTable(String dataset, String table) {
        access.require(Privilege.SELECT, Securable.table(dataset, table));
    }

    public void requireDataset(String dataset) {
        if (!access.sees(dataset)) {
            throw new AccessDeniedException(access.caller().id() + " cannot see " + dataset);
        }
    }

    public void requireServed(ServedTable served) {
        if (!reads(access.caller(), served)) {
            throw new AccessDeniedException(access.caller().id() + " lacks SELECT on " + served.sqlName());
        }
    }

    public boolean reads(Caller caller, ServedTable served) {
        return access.can(caller, Privilege.SELECT, Securable.table(served.dataset(), served.from()));
    }
}
