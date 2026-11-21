package com.dataflow.workspace.impact;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.catalog.Catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SpecImpact {

    static final List<String> FACT_TABLES = List.of(Catalog.SILVER, Catalog.HISTORY, Catalog.CURRENT);

    private SpecImpact() {}

    public static List<Change> compare(PipelineSpec before, PipelineSpec after) {
        List<Change> changes = before == null ? List.of(created(after)) : diff(before, after);
        return changes.stream()
                .sorted(Comparator.comparing(Change::severity).reversed())
                .toList();
    }

    private static Change created(PipelineSpec spec) {
        return new Change(
                Severity.SAFE,
                Area.DATASET,
                "New dataset " + spec.dataset(),
                "Creates %d lake tables and serves %d table(s) once it first runs"
                        .formatted(Catalog.tables(spec).size() - 1, spec.serve().size()),
                Catalog.tables(spec).stream().map(table -> table.id()).toList());
    }

    private static List<Change> diff(PipelineSpec before, PipelineSpec after) {
        List<Change> changes = new ArrayList<>();
        if (!Objects.equals(before.description(), after.description())
                || !Objects.equals(before.owner(), after.owner())) {
            changes.add(new Change(
                    Severity.INFO, Area.DATASET, "Description or owner changed", "Shown in the catalog and the tower", List.of()));
        }
        if (!Objects.equals(before.schedule(), after.schedule())) {
            changes.add(new Change(
                    Severity.INFO,
                    Area.SCHEDULE,
                    "Schedule " + Objects.toString(before.schedule(), "none") + " → "
                            + Objects.toString(after.schedule(), "none"),
                    "The tower picks it up on reload",
                    List.of()));
        }
        changes.addAll(SourceChanges.between(before, after));
        changes.addAll(new ColumnChanges(after.changeColumns())
                .between(before.silver().columns(), after.silver().columns()));
        HistoryChanges.versions(before, after).ifPresent(changes::add);
        changes.addAll(new DerivedChanges().between(before.silver().derive(), after.silver().derive()));
        changes.addAll(new RuleChanges().between(before.silver().expectations(), after.silver().expectations()));
        changes.addAll(HistoryChanges.between(before.history(), after.history()));
        changes.addAll(new ReferenceChanges().between(before.references(), after.references()));
        changes.addAll(new GoldChanges().between(before.gold(), after.gold()));
        changes.addAll(new ServeChanges().between(before.serve(), after.serve()));
        return changes;
    }
}
