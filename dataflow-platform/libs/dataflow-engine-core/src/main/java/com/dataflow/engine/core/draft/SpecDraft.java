package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.draft.Inference.Guess;
import com.dataflow.spec.ExpectationSpec;

import java.util.List;
import java.util.Locale;
import java.util.Map;

record SpecDraft(
        String dataset,
        SourceLocation location,
        SourceFormat format,
        String schedule,
        List<Guess> columns,
        List<ExpectationSpec> rules,
        List<String> keys,
        String sequenceBy,
        String time,
        String category) {

    private static final String CLOSE = " }\n";

    private static String quoted(String value) {
        return Names.identifier(value) ? value : "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static String pad(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }

    private static String option(Map.Entry<String, String> option) {
        return option.getKey() + ": \"" + option.getValue().replace("\t", "\\t") + "\"";
    }

    String yaml() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("dataset: ").append(dataset).append('\n');
        yaml.append("version: 3\n");
        yaml.append("description: Drafted from ")
                .append(location.original().substring(location.original().lastIndexOf('/') + 1))
                .append("\n\n");
        source(yaml);
        if (schedule != null) {
            yaml.append("\nschedule: \"").append(schedule).append("\"\n");
        }
        yaml.append("\nbronze:\n  schema-evolution: rescue\n");
        silver(yaml);
        history(yaml);
        gold(yaml);
        serve(yaml);
        return yaml.toString();
    }

    private void source(StringBuilder yaml) {
        yaml.append("source:\n");
        boolean url = location.template().startsWith("http://") || location.template().startsWith("https://");
        yaml.append(url ? "  url: " : "  path: ").append(location.template()).append('\n');
        yaml.append("  format: ").append(format.format()).append('\n');
        if (!format.options().isEmpty()) {
            yaml.append("  options: { ")
                    .append(String.join(
                            ", ",
                            format.options().entrySet().stream()
                                    .map(SpecDraft::option)
                                    .toList()))
                    .append(CLOSE);
        }
        if (location.partitioned()) {
            yaml.append("  partitions: { ")
                    .append(location.partitionKind())
                    .append(": { start: ")
                    .append(location.partitionStart())
                    .append(" } }\n");
        }
    }

    private void silver(StringBuilder yaml) {
        yaml.append("\nsilver:\n  columns:\n");
        int width = columns.stream().mapToInt(column -> column.name().length()).max().orElse(0);
        for (Guess column : columns) {
            yaml.append("    - { name: ")
                    .append(pad(column.name() + ",", width + 1))
                    .append(" type: ")
                    .append(column.type())
                    .append(",")
                    .append(column.required() ? " required: true," : "")
                    .append(" from: ")
                    .append(quoted(column.from()))
                    .append(CLOSE);
        }
        if (!rules.isEmpty()) {
            yaml.append("  expectations:\n");
            for (ExpectationSpec rule : rules) {
                yaml.append("    - { name: ")
                        .append(rule.name())
                        .append(", check: \"")
                        .append(rule.check())
                        .append("\", on-failure: ")
                        .append(rule.onFailure().name().toLowerCase(Locale.ROOT))
                        .append(CLOSE);
            }
        }
    }

    private void history(StringBuilder yaml) {
        yaml.append("\nhistory:\n  keys: [").append(String.join(", ", keys)).append("]\n");
        if (sequenceBy != null) {
            yaml.append("  sequence-by: ").append(sequenceBy).append('\n');
            yaml.append("  track-history-except: [").append(sequenceBy).append("]\n");
        }
    }

    private void gold(StringBuilder yaml) {
        if (time == null) {
            return;
        }
        yaml.append("\ngold:\n  - name: daily_counts\n    sql: |\n");
        yaml.append("      SELECT to_date(")
                .append(time)
                .append(") AS day")
                .append(category == null ? "" : ", " + category)
                .append(", count(*) AS rows\n      FROM current\n      GROUP BY ")
                .append(category == null ? "1" : "1, 2")
                .append('\n');
    }

    private void serve(StringBuilder yaml) {
        yaml.append("\nserve:\n  - { from: current, table: ").append(dataset).append(", mode: view }\n");
        if (time != null) {
            yaml.append("  - { from: gold.daily_counts, table: ")
                    .append(dataset)
                    .append("_daily_counts, mode: view }\n");
        }
    }
}
