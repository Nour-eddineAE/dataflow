package com.dataflow.spec;

import java.util.regex.Pattern;

public record PartitionsSpec(Window monthly, Window daily) {

    public Kind kind() {
        return monthly != null ? Kind.MONTHLY : Kind.DAILY;
    }

    public String start() {
        return monthly != null ? monthly.start() : daily.start();
    }

    public enum Kind {
        MONTHLY(Pattern.compile("\\d{4}-\\d{2}")),
        DAILY(Pattern.compile("\\d{4}-\\d{2}-\\d{2}"));

        private final Pattern key;

        Kind(Pattern key) {
            this.key = key;
        }

        public boolean accepts(String partition) {
            return partition != null && key.matcher(partition).matches();
        }
    }

    public record Window(String start) {}
}
