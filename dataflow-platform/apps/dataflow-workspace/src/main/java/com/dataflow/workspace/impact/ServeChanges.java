package com.dataflow.workspace.impact;

import com.dataflow.spec.ServeSpec;
import com.dataflow.workspace.catalog.Catalog;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class ServeChanges implements ItemChanges<ServeSpec> {

    private static List<String> table(ServeSpec serve) {
        return List.of(Catalog.SERVING + "." + serve.table());
    }

    private static String mode(ServeSpec serve) {
        return serve.mode().name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String name(ServeSpec serve) {
        return serve.table();
    }

    @Override
    public Change added(ServeSpec serve) {
        return new Change(
                Severity.SAFE,
                Area.SERVING,
                "New served table " + serve.table() + " (" + mode(serve) + ")",
                "Created in ClickHouse on the next run from " + serve.from(),
                table(serve));
    }

    @Override
    public Optional<Change> changed(ServeSpec before, ServeSpec after) {
        if (before.mode() == after.mode()
                && Objects.equals(before.from(), after.from())
                && Objects.equals(before.orderBy(), after.orderBy())) {
            return Optional.empty();
        }
        return Optional.of(new Change(
                Severity.DATA,
                Area.SERVING,
                "Served table " + after.table() + " changed",
                mode(before) + " of " + before.from() + " → " + mode(after) + " of " + after.from()
                        + ": replaced on the next run; queries during the switch may fail",
                table(after)));
    }

    @Override
    public Change removed(ServeSpec serve) {
        return new Change(
                Severity.DATA,
                Area.SERVING,
                "Served table " + serve.table() + " removed",
                "It stays in ClickHouse until dropped by hand; dashboards reading it keep old data",
                table(serve));
    }
}
