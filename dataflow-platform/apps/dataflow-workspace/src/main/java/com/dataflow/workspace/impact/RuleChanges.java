package com.dataflow.workspace.impact;

import com.dataflow.spec.ExpectationSpec;
import com.dataflow.spec.ExpectationSpec.OnFailure;
import com.dataflow.workspace.catalog.Catalog;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class RuleChanges implements ItemChanges<ExpectationSpec> {

    private static String action(OnFailure onFailure) {
        return onFailure.name().toLowerCase(Locale.ROOT);
    }

    private static String consequence(OnFailure onFailure) {
        return switch (onFailure) {
            case WARN -> "rows that break it are counted and kept";
            case DROP -> "rows that break it go to quarantine instead of silver";
            case FAIL -> "one row breaking it fails the whole run";
        };
    }

    @Override
    public String name(ExpectationSpec rule) {
        return rule.name();
    }

    @Override
    public Change added(ExpectationSpec rule) {
        return new Change(
                switch (rule.onFailure()) {
                    case FAIL -> Severity.RISKY;
                    case DROP -> Severity.DATA;
                    case WARN -> Severity.SAFE;
                },
                Area.QUALITY,
                "New rule " + rule.name() + " (" + action(rule.onFailure()) + ")",
                "`" + rule.check() + "`: " + consequence(rule.onFailure()),
                rule.onFailure() == OnFailure.DROP
                        ? List.of(Catalog.SILVER, Catalog.QUARANTINE)
                        : List.of(Catalog.SILVER));
    }

    @Override
    public Optional<Change> changed(ExpectationSpec before, ExpectationSpec after) {
        if (before.check().equals(after.check()) && before.onFailure() == after.onFailure()) {
            return Optional.empty();
        }
        return Optional.of(new Change(
                after.onFailure() == OnFailure.FAIL ? Severity.RISKY : Severity.DATA,
                Area.QUALITY,
                "Rule " + after.name() + " changed",
                "`" + before.check() + "` (" + action(before.onFailure()) + ") → `" + after.check() + "` ("
                        + action(after.onFailure()) + "): " + consequence(after.onFailure()),
                List.of(Catalog.SILVER, Catalog.QUARANTINE)));
    }

    @Override
    public Change removed(ExpectationSpec rule) {
        boolean dropped = rule.onFailure() == OnFailure.DROP;
        return new Change(
                dropped ? Severity.DATA : Severity.SAFE,
                Area.QUALITY,
                "Rule " + rule.name() + " removed",
                dropped ? "Rows it used to quarantine reach silver and history from now on" : "Stops being counted",
                List.of(Catalog.SILVER));
    }
}
