package com.dataflow.workspace.impact;

import com.dataflow.spec.ColumnSpec;

import java.util.List;
import java.util.Optional;

final class ColumnChanges implements ItemChanges<ColumnSpec> {

    private final List<String> changeColumns;

    ColumnChanges(List<String> changeColumns) {
        this.changeColumns = changeColumns;
    }

    @Override
    public String name(ColumnSpec column) {
        return column.name();
    }

    @Override
    public Change added(ColumnSpec column) {
        boolean versions = changeColumns.contains(column.name());
        return new Change(
                versions ? Severity.DATA : Severity.SAFE,
                Area.SILVER,
                "New column " + column.name() + " " + column.type(),
                "Silver and history gain it; versions written before stay NULL."
                        + (versions
                                ? " It counts as a change, so every fact that arrives again gets a new version."
                                : ""),
                SpecImpact.FACT_TABLES);
    }

    @Override
    public Optional<Change> changed(ColumnSpec before, ColumnSpec after) {
        if (!before.type().equalsIgnoreCase(after.type())) {
            return Optional.of(new Change(
                    Severity.RISKY,
                    Area.SILVER,
                    "Type of " + after.name() + ": " + before.type() + " → " + after.type(),
                    "Delta rejects a type change on existing tables: the next run fails until the tables are"
                            + " rebuilt",
                    SpecImpact.FACT_TABLES));
        }
        if (!before.from().equals(after.from())) {
            return Optional.of(new Change(
                    Severity.DATA,
                    Area.SILVER,
                    "Expression of " + after.name() + " changed",
                    "`" + before.from() + "` → `" + after.from() + "`. Rows that arrive from now on use it; "
                            + "versions already written keep the old value"
                            + (changeColumns.contains(after.name())
                                    ? ", and a fact whose value differs gets a new version"
                                    : ""),
                    SpecImpact.FACT_TABLES));
        }
        if (before.required() != after.required()) {
            return Optional.of(new Change(
                    Severity.INFO,
                    Area.SILVER,
                    after.name() + (after.required() ? " is now required" : " is now optional"),
                    "Changes the column's nullability in the spec schema",
                    SpecImpact.FACT_TABLES));
        }
        return Optional.empty();
    }

    @Override
    public Change removed(ColumnSpec column) {
        return new Change(
                Severity.RISKY,
                Area.SILVER,
                "Column " + column.name() + " removed",
                "Existing tables keep the column; new rows leave it empty. Gold SQL or serving that reads it"
                        + " breaks",
                SpecImpact.FACT_TABLES);
    }
}
