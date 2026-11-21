package com.dataflow.engine.core.draft;

import com.dataflow.spec.Names;
import com.dataflow.spec.SourceSpec;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SourceLocation(
        String original,
        String template,
        String partitionKind,
        String partitionStart,
        String format,
        String datasetName) {

    private static final Pattern DAY = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern MONTH = Pattern.compile("(\\d{4})-(\\d{2})");
    private static final Pattern NOT_NAME = Pattern.compile("[^a-z0-9]+");

    public static SourceLocation analyze(String location) {
        String file = location.substring(location.lastIndexOf('/') + 1);
        int query = file.indexOf('?');
        if (query >= 0) {
            file = file.substring(0, query);
        }
        String kind = null;
        String start = null;
        String template = location;
        Matcher day = lastMatch(DAY, file);
        Matcher month = day == null ? lastMatch(MONTH, file) : null;
        Matcher found = day != null ? day : month;
        if (found != null) {
            kind = day != null ? "daily" : "monthly";
            start = found.group();
            int at = location.lastIndexOf(file) + found.start();
            template = location.substring(0, at)
                    + SourceSpec.PARTITION_PLACEHOLDER
                    + location.substring(at + start.length());
        }
        String stem = found == null ? file : file.substring(0, found.start()) + file.substring(found.end());
        return new SourceLocation(
                location, template, kind, start, formatOf(file).orElse(null), datasetName(stem));
    }

    static Optional<String> formatOf(String file) {
        String name = file.toLowerCase(Locale.ROOT);
        for (String compressed : new String[] {".gz", ".bz2", ".zst", ".snappy"}) {
            if (name.endsWith(compressed)) {
                name = name.substring(0, name.length() - compressed.length());
            }
        }
        if (name.endsWith(".csv") || name.endsWith(".tsv") || name.endsWith(".txt")) {
            return Optional.of("csv");
        }
        if (name.endsWith(".json") || name.endsWith(".jsonl") || name.endsWith(".ndjson")) {
            return Optional.of("json");
        }
        if (name.endsWith(".parquet")) {
            return Optional.of("parquet");
        }
        if (name.endsWith(".orc")) {
            return Optional.of("orc");
        }
        return Optional.empty();
    }

    static String datasetName(String stem) {
        String base = stem.toLowerCase(Locale.ROOT);
        int dot = base.indexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String name = NOT_NAME.matcher(base).replaceAll("_").replaceAll("^_+|_+$", "");
        if (name.isEmpty() || !Character.isLetter(name.charAt(0))) {
            name = "source_" + name;
        }
        return Names.truncated(name);
    }

    private static Matcher lastMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int start = -1;
        while (matcher.find()) {
            start = matcher.start();
        }
        if (start < 0) {
            return null;
        }
        Matcher last = pattern.matcher(text);
        return last.find(start) ? last : null;
    }

    public boolean partitioned() {
        return partitionKind != null;
    }
}
