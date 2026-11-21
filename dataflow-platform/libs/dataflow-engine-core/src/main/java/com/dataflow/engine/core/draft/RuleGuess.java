package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.draft.Inference.Guess;
import com.dataflow.engine.core.draft.Inference.Reason;
import com.dataflow.spec.ExpectationSpec;
import com.dataflow.spec.ExpectationSpec.OnFailure;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class RuleGuess {

    private static final Pattern NOT_A_MEASURE = Pattern.compile("(?i).*(^lat|^lon|^lng|latitude|longitude|id)$");
    private static final Set<String> NUMERIC = Set.of("int", "bigint", "smallint", "tinyint", "double", "float");
    private static final int MAX_RANGES = 3;
    private static final int MAX_ACCEPTED = 8;
    private static final BigDecimal RANGE_MARGIN = BigDecimal.valueOf(0.1);

    private RuleGuess() {}

    static List<ExpectationSpec> of(
            Dataset<Row> sample, SampleProfile profile, List<String> keys, List<Reason> reasons) {
        List<ExpectationSpec> rules = new ArrayList<>();
        for (String key : keys.size() > 2 ? List.<String>of() : keys) {
            rules.add(new ExpectationSpec(
                    "has_" + Names.snake(key), key + " IS NOT NULL", OnFailure.DROP));
        }
        List<Guess> measures = profile.columns().stream()
                .filter(column -> !keys.contains(column.name())
                        && column.min() != null
                        && numeric(column.type())
                        && !NOT_A_MEASURE.matcher(column.name()).matches())
                .limit(MAX_RANGES)
                .toList();
        for (Guess column : measures) {
            rules.add(range(column, reasons));
        }
        for (Guess column : profile.columns()) {
            if (!keys.contains(column.name())) {
                known(sample, column, profile.rows(), reasons).ifPresent(rules::add);
            }
        }
        return rules;
    }

    private static ExpectationSpec range(Guess column, List<Reason> reasons) {
        BigDecimal lo = new BigDecimal(column.min());
        BigDecimal hi = new BigDecimal(column.max());
        BigDecimal margin = hi.subtract(lo).multiply(RANGE_MARGIN).max(BigDecimal.ONE);
        BigDecimal floor = lo.subtract(margin);
        String low = (lo.signum() >= 0 ? floor.max(BigDecimal.ZERO) : floor)
                .setScale(0, RoundingMode.FLOOR)
                .toPlainString();
        String high = hi.add(margin).setScale(0, RoundingMode.CEILING).toPlainString();
        String name = "plausible_" + Names.snake(column.name());
        reasons.add(new Reason(
                "rule " + name,
                "seen from " + column.min() + " to " + column.max() + " in the sample; 10% margin"
                        + (lo.signum() >= 0 ? ", never below 0" : "")));
        return new ExpectationSpec(name, column.name() + " BETWEEN " + low + " AND " + high, OnFailure.WARN);
    }

    private static Optional<ExpectationSpec> known(
            Dataset<Row> sample, Guess column, long rows, List<Reason> reasons) {
        long seen = rows - column.nulls();
        if (!column.type().equals("string")
                || column.distinct() < 2
                || column.distinct() > MAX_ACCEPTED
                || column.distinct() * 2 > seen) {
            return Optional.empty();
        }
        List<String> values = sample.select(Names.column(column.source()))
                .distinct()
                .na()
                .drop()
                .limit(MAX_ACCEPTED + 1)
                .as(Encoders.STRING())
                .collectAsList();
        if (values.size() > MAX_ACCEPTED || values.stream().anyMatch(RuleGuess::needsEscaping)) {
            return Optional.empty();
        }
        String name = "known_" + Names.snake(column.name());
        reasons.add(new Reason("rule " + name, values.size() + " values in the sample"));
        return Optional.of(new ExpectationSpec(
                name,
                column.name() + " IN ("
                        + String.join(", ", values.stream().sorted().map(value -> "'" + value + "'").toList()) + ")",
                OnFailure.WARN));
    }

    private static boolean numeric(String type) {
        return NUMERIC.contains(type) || type.startsWith("decimal");
    }

    private static boolean needsEscaping(String value) {
        return value.contains("'") || value.contains("\"") || value.contains("\\");
    }
}
