package com.dataflow.workspace.impact;

import com.dataflow.spec.ColumnSpec;

import java.util.Optional;

final class DerivedChanges implements ItemChanges<ColumnSpec> {

    @Override
    public String name(ColumnSpec column) {
        return column.name();
    }

    @Override
    public Change added(ColumnSpec column) {
        return new Change(
                Severity.SAFE,
                Area.SILVER,
                "New derived column " + column.name(),
                "Added to history and computed for every existing version before the next merge; "
                        + "derived columns never make a new version",
                SpecImpact.FACT_TABLES);
    }

    @Override
    public Optional<Change> changed(ColumnSpec before, ColumnSpec after) {
        if (!before.type().equalsIgnoreCase(after.type())) {
            return Optional.of(new Change(
                    Severity.RISKY,
                    Area.SILVER,
                    "Type of derived " + after.name() + ": " + before.type() + " → " + after.type(),
                    "Delta rejects a type change on existing tables",
                    SpecImpact.FACT_TABLES));
        }
        if (!before.from().equals(after.from())) {
            return Optional.of(new Change(
                    Severity.DATA,
                    Area.SILVER,
                    "Derived " + after.name() + " computed differently",
                    "New arrivals use `" + after.from() + "`; versions already written keep the old value",
                    SpecImpact.FACT_TABLES));
        }
        return Optional.empty();
    }

    @Override
    public Change removed(ColumnSpec column) {
        return new Change(
                Severity.RISKY,
                Area.SILVER,
                "Derived column " + column.name() + " removed",
                "Existing tables keep it; gold SQL or serving that reads it breaks",
                SpecImpact.FACT_TABLES);
    }
}
