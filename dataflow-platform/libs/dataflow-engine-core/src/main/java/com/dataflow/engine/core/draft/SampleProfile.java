package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.draft.Inference.Guess;
import com.dataflow.engine.core.draft.Inference.Reason;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.NumericType;
import org.apache.spark.sql.types.StructField;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.apache.spark.sql.functions.approx_count_distinct;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.max;
import static org.apache.spark.sql.functions.min;
import static org.apache.spark.sql.functions.try_to_timestamp;

record SampleProfile(long rows, List<Guess> columns) {

    private static final String ROWS = "__rows";

    static SampleProfile of(Dataset<Row> sample, SourceFormat format, List<Reason> reasons) {
        StructField[] fields = sample.schema().fields();
        Row profile = profile(sample, fields);
        long rows = profile.getLong(profile.fieldIndex(ROWS));
        reasons.add(new Reason("sample: " + rows + " rows", "every choice below is read from these rows only"));
        List<Guess> columns = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < fields.length; i++) {
            StructField field = fields[i];
            Stats stats = Stats.of(profile, i, field.dataType());
            boolean timestamp = timestamp(field.dataType(), stats, format);
            String name = Names.unique(Names.camel(field.name()), names);
            columns.add(new Guess(
                    field.name(),
                    name,
                    timestamp ? "timestamp" : ddl(field.dataType()),
                    from(field, timestamp, format),
                    rows > 0 && stats.nonNull() == rows,
                    rows - stats.nonNull(),
                    stats.distinct(),
                    stats.min(),
                    stats.max()));
            if (timestamp) {
                reasons.add(new Reason(
                        name + ": timestamp", "every value of " + field.name() + " reads as a date and time"));
            }
            if (!name.equals(field.name())) {
                reasons.add(new Reason(name, "camelCase of " + field.name()));
            }
        }
        return new SampleProfile(rows, columns);
    }

    private static Row profile(Dataset<Row> sample, StructField[] fields) {
        List<Column> aggregates = new ArrayList<>();
        aggregates.add(count(lit(1)).as(ROWS));
        for (int i = 0; i < fields.length; i++) {
            Column column = Names.column(fields[i].name());
            DataType type = fields[i].dataType();
            aggregates.add(count(column).as("n" + i));
            aggregates.add(approx_count_distinct(column).as("d" + i));
            if (orderable(type)) {
                aggregates.add(min(column).cast("string").as("lo" + i));
                aggregates.add(max(column).cast("string").as("hi" + i));
            }
            if (type.equals(DataTypes.StringType)) {
                aggregates.add(count(try_to_timestamp(column)).as("t" + i));
            }
        }
        return sample.agg(aggregates.getFirst(), aggregates.subList(1, aggregates.size()).toArray(Column[]::new))
                .first();
    }

    private static boolean timestamp(DataType type, Stats stats, SourceFormat format) {
        boolean inferred = format.textual()
                && (type.equals(DataTypes.TimestampType) || type.equals(DataTypes.TimestampNTZType));
        return inferred
                || type.equals(DataTypes.StringType) && stats.nonNull() > 0 && stats.timestamps() == stats.nonNull();
    }

    private static String from(StructField field, boolean timestamp, SourceFormat format) {
        String source = Names.sql(field.name());
        if (timestamp) {
            return "to_timestamp(" + source + ")";
        }
        return format.textual() && field.dataType().equals(DataTypes.DateType) ? "to_date(" + source + ")" : source;
    }

    private static boolean orderable(DataType type) {
        return type instanceof NumericType
                || type.equals(DataTypes.DateType)
                || type.equals(DataTypes.TimestampType)
                || type.equals(DataTypes.TimestampNTZType);
    }

    private static String ddl(DataType type) {
        String sql = type.sql().toLowerCase(Locale.ROOT);
        return sql.equals("integer") ? "int" : sql;
    }

    private record Stats(long nonNull, long distinct, long timestamps, String min, String max) {

        static Stats of(Row profile, int i, DataType type) {
            boolean ordered = orderable(type);
            return new Stats(
                    profile.getLong(profile.fieldIndex("n" + i)),
                    profile.getLong(profile.fieldIndex("d" + i)),
                    type.equals(DataTypes.StringType) ? profile.getLong(profile.fieldIndex("t" + i)) : 0,
                    ordered ? profile.getString(profile.fieldIndex("lo" + i)) : null,
                    ordered ? profile.getString(profile.fieldIndex("hi" + i)) : null);
        }
    }
}
