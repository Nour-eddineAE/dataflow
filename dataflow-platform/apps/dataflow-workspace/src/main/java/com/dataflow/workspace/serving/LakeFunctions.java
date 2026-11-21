package com.dataflow.workspace.serving;

import com.dataflow.spec.lake.ObjectStore;
import com.dataflow.workspace.config.WorkspaceProperties;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Component;

import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.unquotedName;

@Component
public class LakeFunctions {

    private final Name collection;
    private final String delta;
    private final String objects;
    private final String prefix;

    public LakeFunctions(WorkspaceProperties properties) {
        this.collection = unquotedName(properties.serving().collection());
        boolean azure = ObjectStore.of(properties.serving().lakeRoot()) == ObjectStore.AZURE;
        this.delta = azure ? "deltaLakeAzure({0}, blob_path = {1})" : "deltaLake({0}, filename = {1})";
        this.objects = azure
                ? "azureBlobStorage({0}, blob_path = {1}, format = {2})"
                : "s3({0}, filename = {1}, format = {2})";
        this.prefix = azure ? ObjectStore.prefix(properties.serving().lakeRoot()) : "";
    }

    public Table<Record> delta(String key) {
        return table(delta, collection, inline(prefix + key));
    }

    public Table<Record> objects(String pattern) {
        return table(objects, collection, inline(prefix + pattern), inline("One"));
    }

    public Table<Record> blob(String key) {
        return table(objects, collection, inline(prefix + key), inline("RawBLOB"));
    }

    public Table<Record> jsonDocuments(String pattern) {
        return table(objects, collection, inline(prefix + pattern), inline("JSONAsString"));
    }
}
