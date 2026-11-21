package com.dataflow.spec.spark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DoubleType;
import org.apache.spark.sql.types.FloatType;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.types.TimestampNTZType;
import org.apache.spark.sql.types.TimestampType;

import java.util.List;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.concat_ws;
import static org.apache.spark.sql.functions.date_format;
import static org.apache.spark.sql.functions.expr;
import static org.apache.spark.sql.functions.format_string;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.sha2;

public final class CanonicalEncoding {

    private static final String SEP = "\u001F";
    private static final int SHA_BITS = 256;
    private static final String DOUBLE_FORMAT = "%.6f";
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String STRING_FORMAT = "string";

    private CanonicalEncoding() {}

    public static Column sha256(StructType schema, List<String> fields) {
        Column[] parts = fields.stream()
                .map(name -> canonical(schema.apply(name).dataType(), name))
                .toArray(Column[]::new);
        return sha2(concat_ws(SEP, parts), SHA_BITS);
    }

    private static Column canonical(DataType type, String fieldName) {
        Column c = col(fieldName);
        Column s;
        if (type instanceof FloatType || type instanceof DoubleType) {
            s = format_string(DOUBLE_FORMAT, c);
        } else if (type instanceof TimestampType) {
            s = date_format(expr(String.format("to_utc_timestamp(`%s`, current_timezone())", fieldName)), DATE_FORMAT);
        } else if (type instanceof TimestampNTZType) {
            s = date_format(c, DATE_FORMAT);
        } else {
            s = c.cast(STRING_FORMAT);
        }
        return coalesce(s, lit(""));
    }
}
