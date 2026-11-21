package com.dataflow.workspace.why;

import com.dataflow.spec.ColumnSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.workspace.catalog.Catalog;
import com.dataflow.workspace.catalog.LakeTable;
import com.dataflow.workspace.catalog.TableService;
import com.dataflow.workspace.lineage.ColumnLineage;
import com.dataflow.workspace.lineage.Lineage;
import com.dataflow.workspace.serving.QueryResult;
import com.dataflow.workspace.specs.SpecStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class WhyService {

    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern PLAIN_COLUMN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final SpecStore specs;
    private final TableService tables;
    private final Facts facts;

    public WhyService(SpecStore specs, TableService tables, Facts facts) {
        this.specs = specs;
        this.tables = tables;
        this.facts = facts;
    }

    private static Map<String, Object> latest(QueryResult versions) {
        Map<String, Object> current = new LinkedHashMap<>();
        List<Object> last = versions.rows().getLast();
        for (int i = 0; i < versions.columns().size(); i++) {
            current.put(versions.columns().get(i).name(), last.get(i));
        }
        return current;
    }

    private static String column(PipelineSpec spec, String column) {
        if (column == null || column.isBlank()) {
            return null;
        }
        if (!List.of(spec.silverSchema().fieldNames()).contains(column)) {
            throw new IllegalArgumentException(spec.dataset() + " silver has no column " + column);
        }
        return column;
    }

    private static String expression(PipelineSpec spec, String column) {
        if (column == null) {
            return null;
        }
        return Stream.concat(spec.silver().columns().stream(), spec.silver().derive().stream())
                .filter(c -> c.name().equals(column))
                .map(ColumnSpec::from)
                .findFirst()
                .orElse(null);
    }

    public Why explain(String dataset, String id, String column) {
        PipelineSpec spec = specs.spec(dataset);
        LakeTable history = written(dataset, Catalog.HISTORY)
                .orElseThrow(() -> new IllegalArgumentException(dataset + " has no history yet; run it first"));
        String fact = factId(spec, history, id);
        String chosen = column(spec, column);
        QueryResult versions = facts.versions(history, fact);
        if (versions.rows().isEmpty()) {
            throw new IllegalArgumentException("No fact " + fact + " in " + dataset);
        }
        Map<String, Object> current = latest(versions);
        QueryResult arrivals = written(dataset, Catalog.SILVER)
                .map(silver -> facts.arrivals(silver, fact, chosen))
                .orElse(null);
        QueryResult quarantined = spec.quarantines()
                ? written(dataset, Catalog.QUARANTINE)
                        .map(quarantine -> facts.quarantined(quarantine, fact, chosen))
                        .orElse(null)
                : null;
        RawRow raw = raw(spec, arrivals, current);
        Lineage lineage = specs.parsing().run(() -> ColumnLineage.of(spec));
        return new Why(
                dataset,
                fact,
                chosen,
                expression(spec, chosen),
                current,
                versions,
                arrivals,
                quarantined,
                raw.rows(),
                raw.note(),
                chosen == null ? List.of() : lineage.upstream(Catalog.SILVER, chosen),
                chosen == null ? List.of() : lineage.downstream(Catalog.SILVER, chosen));
    }

    private Optional<LakeTable> written(String dataset, String table) {
        return Optional.of(tables.table(dataset, table)).filter(LakeTable::exists);
    }

    private String factId(PipelineSpec spec, LakeTable history, String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Give a factUniqueId or a key value");
        }
        if (HASH.matcher(id).matches()) {
            return id;
        }
        if (spec.history().keys().size() != 1) {
            throw new IllegalArgumentException(spec.dataset() + " has a composite key "
                    + spec.history().keys() + "; pick the fact by its factUniqueId");
        }
        String key = spec.history().keys().getFirst();
        return facts.idByKey(history, key, id)
                .orElseThrow(() -> new IllegalArgumentException("No fact with " + key + " = " + id));
    }

    private RawRow raw(PipelineSpec spec, QueryResult arrivals, Map<String, Object> current) {
        Optional<LakeTable> bronze = written(spec.dataset(), Catalog.BRONZE);
        if (bronze.isEmpty() || arrivals == null || arrivals.rows().isEmpty()) {
            return new RawRow(null, "No bronze rows to trace");
        }
        Map<String, String> keyValues = new LinkedHashMap<>();
        for (String key : spec.history().keys()) {
            Optional<ColumnSpec> declared = spec.silver().columns().stream()
                    .filter(c -> c.name().equals(key))
                    .findFirst();
            if (declared.isEmpty()
                    || !PLAIN_COLUMN.matcher(declared.get().from().strip()).matches()) {
                return new RawRow(
                        null,
                        "Key " + key + " is computed ("
                                + declared.map(ColumnSpec::from).orElse("?")
                                + "), so its raw row cannot be looked up by value");
            }
            keyValues.put(declared.get().from().strip(), String.valueOf(current.get(key)));
        }
        String file = String.valueOf(arrivals.rows().getLast().get(arrivals.index(SystemColumns.SOURCE_FILE)));
        return new RawRow(facts.raw(bronze.get(), file, keyValues), "The row as it arrived in " + file);
    }

    private record RawRow(QueryResult rows, String note) {}
}
