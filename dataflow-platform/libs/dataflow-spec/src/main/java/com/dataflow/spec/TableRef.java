package com.dataflow.spec;

import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.lake.Layer;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TableRef(String dataset, Kind kind, String name) {

    public static final String FORMAT =
            "<dataset>.<bronze|silver|quarantine|history|current|gold.<name>|reference.<name>>";

    private static final Pattern TEXT = Pattern.compile(
            "(?<dataset>" + Names.pattern("[a-z0-9_]") + ")\\.(?<kind>bronze|silver|quarantine|history|current|gold|reference)"
                    + "(?:\\.(?<name>" + Names.pattern("[a-z0-9_]") + "))?");
    private static final String SEPARATOR = ".";

    public static Optional<TableRef> parse(String text) {
        Matcher match = TEXT.matcher(text == null ? "" : text);
        if (!match.matches()) {
            return Optional.empty();
        }
        Kind kind = Kind.valueOf(match.group("kind").toUpperCase(Locale.ROOT));
        String name = match.group("name");
        return kind.named() == (name != null)
                ? Optional.of(new TableRef(match.group("dataset"), kind, name))
                : Optional.empty();
    }

    public String path(Lake lake) {
        return kind.named() ? lake.table(kind.layer(), dataset, name) : lake.table(kind.layer(), dataset);
    }

    @Override
    public String toString() {
        String table = dataset + SEPARATOR + kind.segment();
        return kind.named() ? table + SEPARATOR + name : table;
    }

    public enum Kind {
        BRONZE(Layer.BRONZE, false),
        SILVER(Layer.SILVER, false),
        QUARANTINE(Layer.QUARANTINE, false),
        HISTORY(Layer.HISTORY, false),
        CURRENT(Layer.HISTORY, false),
        GOLD(Layer.GOLD, true),
        REFERENCE(Layer.REFERENCE, true);

        private final Layer layer;
        private final boolean named;

        Kind(Layer layer, boolean named) {
            this.layer = layer;
            this.named = named;
        }

        public Layer layer() {
            return layer;
        }

        public boolean named() {
            return named;
        }

        public String segment() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
