package com.dataflow.engine.core.flows;

import com.dataflow.spec.SystemColumns;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.map;
import static org.apache.spark.sql.functions.map_filter;
import static org.apache.spark.sql.functions.size;
import static org.apache.spark.sql.functions.to_json;
import static org.apache.spark.sql.functions.when;

final class Rescue {

    private Rescue() {}

    static Dataset<Row> conform(Dataset<Row> raw, StructType target) {
        Map<String, StructField> byName = new LinkedHashMap<>();
        for (StructField field : raw.schema().fields()) {
            byName.put(field.name().toLowerCase(Locale.ROOT), field);
        }
        Set<String> matched = new HashSet<>();
        List<Column> columns = new ArrayList<>();
        List<Column> rescued = new ArrayList<>();
        for (StructField wanted : target.fields()) {
            StructField found = byName.get(wanted.name().toLowerCase(Locale.ROOT));
            if (found == null) {
                columns.add(lit(null).cast(wanted.dataType()).alias(wanted.name()));
                continue;
            }
            matched.add(found.name());
            Column source = col(quoted(found.name()));
            if (found.dataType().equals(wanted.dataType())) {
                columns.add(source.alias(wanted.name()));
            } else {
                Column cast = source.try_cast(wanted.dataType());
                columns.add(cast.alias(wanted.name()));
                rescued.add(lit(found.name()));
                rescued.add(when(cast.isNull().and(source.isNotNull()), source.cast(DataTypes.StringType)));
            }
        }
        for (StructField extra : raw.schema().fields()) {
            if (!matched.contains(extra.name())) {
                rescued.add(lit(extra.name()));
                rescued.add(col(quoted(extra.name())).cast(DataTypes.StringType));
            }
        }
        columns.add(rescuedData(rescued).alias(SystemColumns.RESCUED_DATA));
        return raw.select(columns.toArray(Column[]::new));
    }

    static StructType dataColumns(StructType bronze) {
        StructType data = new StructType();
        for (StructField field : bronze.fields()) {
            if (!field.name().startsWith(SystemColumns.PREFIX)) {
                data = data.add(field);
            }
        }
        return data;
    }

    private static Column rescuedData(List<Column> pairs) {
        if (pairs.isEmpty()) {
            return lit(null).cast(DataTypes.StringType);
        }
        Column kept = map_filter(map(pairs.toArray(Column[]::new)), (key, value) -> value.isNotNull());
        return when(size(kept).gt(0), to_json(kept));
    }

    private static String quoted(String name) {
        return "`" + name.replace("`", "``") + "`";
    }
}
