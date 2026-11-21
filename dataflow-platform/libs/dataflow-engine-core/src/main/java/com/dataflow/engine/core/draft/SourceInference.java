package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.draft.Inference.Guess;
import com.dataflow.engine.core.draft.Inference.Reason;
import com.dataflow.spec.ExpectationSpec;
import com.dataflow.spec.InvalidPipelineSpecException;
import com.dataflow.spec.PipelineSpecLoader;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.storage.StorageLevel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SourceInference {

    private static final Pattern SEQUENCE = Pattern.compile("(?i).*(updated|modified|changed|version|last).*");
    private static final int MAX_CATEGORY = 50;

    private static String sequenceBy(List<Guess> columns, List<String> keys, List<Reason> reasons) {
        String sequenceBy = columns.stream()
                .filter(column -> column.type().startsWith("timestamp") && !keys.contains(column.name()))
                .map(Guess::name)
                .filter(name -> SEQUENCE.matcher(name).matches())
                .findFirst()
                .orElse(null);
        if (sequenceBy != null) {
            reasons.add(new Reason(
                    "sequence-by: " + sequenceBy, "a timestamp named like a revision: the newest version wins"));
        }
        return sequenceBy;
    }

    private static String time(List<Guess> columns, String sequenceBy) {
        return columns.stream()
                .filter(column -> column.type().startsWith("timestamp") && !column.name().equals(sequenceBy))
                .map(Guess::name)
                .findFirst()
                .orElse(sequenceBy);
    }

    private static String category(List<Guess> columns, List<String> keys) {
        return columns.stream()
                .filter(column -> column.type().equals("string") && !keys.contains(column.name()))
                .filter(column -> column.distinct() >= 2 && column.distinct() <= MAX_CATEGORY)
                .map(Guess::name)
                .findFirst()
                .orElse(null);
    }

    private static String schedule(SourceLocation location, List<Reason> reasons) {
        if (location.partitioned()) {
            reasons.add(new Reason("no schedule", "a partitioned source is loaded by backfilling its partitions"));
            return null;
        }
        boolean hourly = location.original().toLowerCase(Locale.ROOT).contains("hour");
        String cron = hourly ? "0 * * * *" : "0 */6 * * *";
        reasons.add(new Reason(
                "schedule: " + cron,
                hourly
                        ? "the source is an hourly feed"
                        : "a source at a fixed address is read again every 6 hours; known files are skipped"));
        return cron;
    }

    private static List<String> problems(String spec, String dataset) {
        try {
            new PipelineSpecLoader().parse(spec, dataset);
            return List.of();
        } catch (InvalidPipelineSpecException e) {
            return e.problems();
        }
    }

    public Inference infer(SparkSession spark, SourceLocation location, Path local, String dataset, int sampleRows) {
        List<Reason> reasons = new ArrayList<>();
        SourceFormat format = SourceFormat.of(location, local, reasons);
        Dataset<Row> sample = spark.read()
                .format(format.format())
                .options(format.reading())
                .load(local.toUri().toString())
                .limit(sampleRows)
                .persist(StorageLevel.DISK_ONLY());
        try {
            return decide(dataset, location, format, sample, reasons);
        } finally {
            sample.unpersist();
        }
    }

    private Inference decide(
            String dataset, SourceLocation location, SourceFormat format, Dataset<Row> sample, List<Reason> reasons) {
        SampleProfile profile = SampleProfile.of(sample, format, reasons);
        List<Guess> columns = profile.columns();
        List<String> keys = KeyGuess.of(sample, profile, reasons);
        String sequenceBy = sequenceBy(columns, keys, reasons);
        List<ExpectationSpec> rules = RuleGuess.of(sample, profile, keys, reasons);
        String schedule = schedule(location, reasons);
        if (location.partitioned()) {
            reasons.add(new Reason(
                    "partitions: " + location.partitionKind() + " from " + location.partitionStart(),
                    "the file name holds the date " + location.partitionStart() + "; it became {partition}"));
        }
        String spec = new SpecDraft(
                        dataset,
                        location,
                        format,
                        schedule,
                        columns,
                        rules,
                        keys,
                        sequenceBy,
                        time(columns, sequenceBy),
                        category(columns, keys))
                .yaml();
        return new Inference(
                dataset,
                format.format(),
                format.options(),
                profile.rows(),
                columns,
                keys,
                sequenceBy,
                schedule,
                spec,
                reasons,
                problems(spec, dataset));
    }
}
