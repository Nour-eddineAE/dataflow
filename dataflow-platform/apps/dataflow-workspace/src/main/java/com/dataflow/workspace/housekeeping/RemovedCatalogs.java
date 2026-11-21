package com.dataflow.workspace.housekeeping;

import com.dataflow.workspace.catalog.Catalog;
import com.dataflow.workspace.housekeeping.Candidate.Kind;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.DatabaseTables;
import com.dataflow.workspace.serving.SystemTables;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.specs.StoredSpec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.dropDatabaseIfExists;
import static org.jooq.impl.DSL.name;

@Service
public class RemovedCatalogs implements Leftovers {

    private final SystemTables system;
    private final ClickHouse serving;
    private final SpecStore specs;

    public RemovedCatalogs(SystemTables system, ClickHouse serving, SpecStore specs) {
        this.system = system;
        this.serving = serving;
        this.specs = specs;
    }

    @Override
    public Kind kind() {
        return Kind.CATALOGS;
    }

    @Override
    public List<Candidate> candidates() {
        Set<String> kept = specs.all().stream()
                .map(StoredSpec::dataset)
                .map(Catalog::database)
                .collect(Collectors.toSet());
        return system.tablesByDatabase().stream()
                .filter(DatabaseTables::onlyLakeViews)
                .filter(database -> Catalog.isDatasetDatabase(database.database()))
                .filter(database -> !kept.contains(database.database()))
                .map(database -> new Candidate(
                        Kind.CATALOGS, database.database(), database.lakeViews(), 0, List.of(database.database())))
                .toList();
    }

    @Override
    public void remove(List<String> databases) {
        databases.forEach(database -> serving.execute(dropDatabaseIfExists(name(database))));
    }
}
