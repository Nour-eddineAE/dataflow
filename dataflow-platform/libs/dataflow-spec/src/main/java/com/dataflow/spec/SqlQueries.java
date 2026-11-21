package com.dataflow.spec;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SqlQueries {

    public static final String RULE = "gold SQL is one SELECT (or WITH ... SELECT) query";

    private static final Pattern QUERY = Pattern.compile("(?is)^(select|with)\\b.*");
    private static final String END = ";";

    private SqlQueries() {}

    public static Optional<String> single(String sql) {
        String query = sql == null ? "" : sql.strip();
        while (query.endsWith(END)) {
            query = query.substring(0, query.length() - END.length()).strip();
        }
        return QUERY.matcher(query).matches() && !query.toLowerCase(Locale.ROOT).contains(END)
                ? Optional.of(query)
                : Optional.empty();
    }
}
