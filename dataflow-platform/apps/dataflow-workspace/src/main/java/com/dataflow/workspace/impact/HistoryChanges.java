package com.dataflow.workspace.impact;

import com.dataflow.spec.ColumnSpec;
import com.dataflow.spec.HistorySpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.catalog.Catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class HistoryChanges {

    private HistoryChanges() {}

    private static Set<String> columns(PipelineSpec spec) {
        return spec.silver().columns().stream().map(ColumnSpec::name).collect(Collectors.toSet());
    }

    static Optional<Change> versions(PipelineSpec before, PipelineSpec after) {
        if (before.changeColumns().equals(after.changeColumns()) || !columns(before).equals(columns(after))) {
            return Optional.empty();
        }
        return Optional.of(new Change(
                Severity.DATA,
                Area.HISTORY,
                "Change columns differ",
                "contentHash covers " + after.changeColumns() + " instead of " + before.changeColumns()
                        + ": every fact that arrives again gets a new version once",
                List.of(Catalog.HISTORY)));
    }

    static List<Change> between(HistorySpec before, HistorySpec after) {
        List<Change> changes = new ArrayList<>();
        if (!before.keys().equals(after.keys())) {
            changes.add(new Change(
                    Severity.RISKY,
                    Area.HISTORY,
                    "Keys " + before.keys() + " → " + after.keys(),
                    "Every factUniqueId changes: facts that arrive again become new facts and the old ones stay "
                            + "current forever. Only do this with a full refresh of history",
                    SpecImpact.FACT_TABLES));
        }
        if (!Objects.equals(before.sequenceBy(), after.sequenceBy())) {
            changes.add(new Change(
                    Severity.DATA,
                    Area.HISTORY,
                    "Sequence-by " + Objects.toString(before.sequenceBy(), "none") + " → "
                            + Objects.toString(after.sequenceBy(), "none"),
                    "Decides which version is newest when several arrive; applies to merges from now on",
                    List.of(Catalog.HISTORY)));
        }
        return changes;
    }
}
