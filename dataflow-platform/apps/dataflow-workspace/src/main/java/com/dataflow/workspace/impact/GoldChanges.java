package com.dataflow.workspace.impact;

import com.dataflow.spec.GoldSpec;
import com.dataflow.workspace.catalog.Catalog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class GoldChanges implements ItemChanges<GoldSpec> {

    private static List<String> table(GoldSpec gold) {
        return List.of(Catalog.GOLD + "." + gold.name());
    }

    private static String sql(GoldSpec gold) {
        return gold.sql() == null ? null : gold.sql().strip();
    }

    private static boolean same(GoldSpec before, GoldSpec after) {
        return Objects.equals(sql(before), sql(after))
                && Objects.equals(before.transformation(), after.transformation())
                && before.inputs().equals(after.inputs())
                && before.params().equals(after.params());
    }

    @Override
    public String name(GoldSpec gold) {
        return gold.name();
    }

    @Override
    public Change added(GoldSpec gold) {
        return new Change(
                Severity.SAFE, Area.GOLD, "New gold table " + gold.name(), "Built from current on the next run", table(gold));
    }

    @Override
    public Optional<Change> changed(GoldSpec before, GoldSpec after) {
        if (same(before, after)) {
            return Optional.empty();
        }
        return Optional.of(new Change(
                Severity.DATA,
                Area.GOLD,
                "Gold " + after.name() + (after.usesTransformation() ? " transformation changed" : " SQL changed"),
                "Rebuilt in full on the next run; a copy served from it is recreated if its columns change",
                table(after)));
    }

    @Override
    public Change removed(GoldSpec gold) {
        return new Change(
                Severity.DATA,
                Area.GOLD,
                "Gold table " + gold.name() + " removed",
                "Its lake table stays but is no longer rebuilt",
                table(gold));
    }
}
