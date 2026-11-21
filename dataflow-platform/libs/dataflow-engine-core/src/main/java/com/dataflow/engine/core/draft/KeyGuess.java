package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.draft.Inference.Guess;
import com.dataflow.engine.core.draft.Inference.Reason;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.apache.spark.sql.functions.countDistinct;

final class KeyGuess {

    private static final int PAIR_CANDIDATES = 6;
    private static final double NEARLY_UNIQUE = 0.95;

    private KeyGuess() {}

    static List<String> of(Dataset<Row> sample, SampleProfile profile, List<Reason> reasons) {
        Optional<Guess> single = single(sample, profile);
        if (single.isPresent()) {
            reasons.add(new Reason("keys: " + single.get().name(), "unique and never empty in the sample"));
            return List.of(single.get().name());
        }
        Optional<List<Guess>> pair = pair(sample, profile);
        if (pair.isPresent()) {
            List<String> keys = pair.get().stream().map(Guess::name).toList();
            reasons.add(new Reason(
                    "keys: " + String.join(", ", keys), "no single column is unique; this pair is, in the sample"));
            return keys;
        }
        reasons.add(new Reason(
                "keys: every column", "no unique column or pair in the sample; review before the first run"));
        return profile.columns().stream().map(Guess::name).toList();
    }

    private static Optional<Guess> single(Dataset<Row> sample, SampleProfile profile) {
        long rows = profile.rows();
        List<Guess> candidates = profile.columns().stream()
                .filter(column -> column.nulls() == 0 && column.distinct() >= rows * NEARLY_UNIQUE)
                .toList();
        if (candidates.isEmpty() || rows == 0) {
            return Optional.empty();
        }
        Row exact = sample.agg(
                        countDistinct(Names.column(candidates.getFirst().source())),
                        candidates.stream()
                                .skip(1)
                                .map(column -> countDistinct(Names.column(column.source())))
                                .toArray(Column[]::new))
                .first();
        List<Guess> unique = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (exact.getLong(i) == rows) {
                unique.add(candidates.get(i));
            }
        }
        return unique.stream()
                .filter(column -> column.name().toLowerCase(Locale.ROOT).endsWith("id"))
                .findFirst()
                .or(() -> unique.stream().filter(column -> column.type().equals("string")).findFirst())
                .or(() -> unique.stream().findFirst());
    }

    private static Optional<List<Guess>> pair(Dataset<Row> sample, SampleProfile profile) {
        List<Guess> pairable = profile.columns().stream()
                .filter(column -> column.nulls() == 0)
                .sorted(Comparator.comparingLong(Guess::distinct).reversed())
                .limit(PAIR_CANDIDATES)
                .toList();
        for (int a = 0; a < pairable.size(); a++) {
            for (int b = a + 1; b < pairable.size(); b++) {
                long distinct = sample.select(
                                Names.column(pairable.get(a).source()), Names.column(pairable.get(b).source()))
                        .distinct()
                        .count();
                if (distinct == profile.rows()) {
                    return Optional.of(List.of(pairable.get(a), pairable.get(b)));
                }
            }
        }
        return Optional.empty();
    }
}
