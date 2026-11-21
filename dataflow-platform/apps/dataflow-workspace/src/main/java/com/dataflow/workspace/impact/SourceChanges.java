package com.dataflow.workspace.impact;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SourceSpec;
import com.dataflow.workspace.catalog.Catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class SourceChanges {

    private SourceChanges() {}

    static List<Change> between(PipelineSpec before, PipelineSpec after) {
        List<Change> changes = new ArrayList<>();
        if (before.ingests() != after.ingests()) {
            changes.add(new Change(
                    Severity.DATA,
                    Area.SOURCE,
                    after.ingests() ? "Source added" : "Source removed",
                    after.ingests()
                            ? "The pipeline now lands files; its bronze, silver and history start empty"
                            : "The pipeline now only reads its inputs; its bronze, silver and history stop growing",
                    List.of(Catalog.LANDING, Catalog.BRONZE)));
            return changes;
        }
        if (!after.ingests()) {
            return changes;
        }
        if (!sameFiles(before.source(), after.source())) {
            changes.add(new Change(
                    Severity.DATA,
                    Area.SOURCE,
                    "Source changed",
                    "The next run lands the new file(s); bronze keeps every earlier row. Columns the old source did"
                            + " not have go to _rescued_data (schema-evolution: "
                            + after.bronze().schemaEvolution().name().toLowerCase(Locale.ROOT).replace('_', '-')
                            + ")",
                    List.of(Catalog.LANDING, Catalog.BRONZE)));
        }
        if (!Objects.equals(before.source().partitions(), after.source().partitions())) {
            changes.add(new Change(
                    Severity.DATA,
                    Area.SOURCE,
                    "Partitions changed",
                    "The tower's partition set changes; partitions already run keep their data",
                    List.of(Catalog.LANDING)));
        }
        if (!before.bronze().equals(after.bronze())) {
            changes.add(new Change(
                    Severity.INFO,
                    Area.BRONZE,
                    "Bronze settings changed",
                    "Applies to files read from now on",
                    List.of(Catalog.BRONZE)));
        }
        return changes;
    }

    private static boolean sameFiles(SourceSpec before, SourceSpec after) {
        return Objects.equals(before.url(), after.url())
                && Objects.equals(before.path(), after.path())
                && Objects.equals(before.format(), after.format())
                && before.options().equals(after.options());
    }
}
