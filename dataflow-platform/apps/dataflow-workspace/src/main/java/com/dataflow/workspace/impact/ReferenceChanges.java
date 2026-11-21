package com.dataflow.workspace.impact;

import com.dataflow.spec.ReferenceSpec;
import com.dataflow.workspace.catalog.Catalog;

import java.util.List;
import java.util.Optional;

final class ReferenceChanges implements ItemChanges<ReferenceSpec> {

    private static List<String> table(ReferenceSpec reference) {
        return List.of(Catalog.REFERENCE + "." + reference.name());
    }

    @Override
    public String name(ReferenceSpec reference) {
        return reference.name();
    }

    @Override
    public Change added(ReferenceSpec reference) {
        return new Change(
                Severity.SAFE,
                Area.REFERENCES,
                "New reference " + reference.name(),
                "Loaded on every run, readable from gold SQL",
                table(reference));
    }

    @Override
    public Optional<Change> changed(ReferenceSpec before, ReferenceSpec after) {
        if (before.equals(after)) {
            return Optional.empty();
        }
        return Optional.of(new Change(
                Severity.DATA,
                Area.REFERENCES,
                "Reference " + after.name() + " changed",
                "Reloaded in full on the next run",
                table(after)));
    }

    @Override
    public Change removed(ReferenceSpec reference) {
        return new Change(
                Severity.DATA,
                Area.REFERENCES,
                "Reference " + reference.name() + " removed",
                "Its lake table stays but is no longer reloaded",
                table(reference));
    }
}
