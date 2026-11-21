package com.dataflow.workspace.why;

import com.dataflow.workspace.catalog.LakeTable;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.QueryResult;
import org.jooq.Condition;
import org.jooq.Field;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.dataflow.workspace.catalog.LakeFields.CONTENT_HASH;
import static com.dataflow.workspace.catalog.LakeFields.FACT_UNIQUE_ID;
import static com.dataflow.workspace.catalog.LakeFields.FAILED_EXPECTATIONS;
import static com.dataflow.workspace.catalog.LakeFields.INGESTED_AT;
import static com.dataflow.workspace.catalog.LakeFields.PROCESSED_AT;
import static com.dataflow.workspace.catalog.LakeFields.RUN_ID;
import static com.dataflow.workspace.catalog.LakeFields.SOURCE_FILE;
import static com.dataflow.workspace.catalog.LakeFields.START_AT;
import static com.dataflow.workspace.serving.ClickHouseDsl.text;
import static org.jooq.impl.DSL.asterisk;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectDistinct;
import static org.jooq.impl.DSL.selectFrom;

@Component
public class Facts {

    private static final int MAX_VERSIONS = 200;
    private static final int MAX_RAW_ROWS = 5;

    private final ClickHouse serving;
    private final LakeFunctions lake;

    public Facts(ClickHouse serving, LakeFunctions lake) {
        this.serving = serving;
        this.lake = lake;
    }

    private static List<Field<?>> withColumn(String column, Field<?>... fields) {
        Stream<Field<?>> chosen = column == null ? Stream.empty() : Stream.of(field(name(column)));
        return Stream.concat(Stream.of(fields), chosen).toList();
    }

    public Optional<String> idByKey(LakeTable history, String key, String value) {
        return serving
                .fetchValues(
                        selectDistinct(FACT_UNIQUE_ID)
                                .from(lake.delta(history.key()))
                                .where(text(field(name(key))).eq(value))
                                .limit(2),
                        String.class)
                .stream()
                .findFirst();
    }

    public QueryResult versions(LakeTable history, String fact) {
        return serving.fetch(
                select(asterisk().except(CONTENT_HASH))
                        .from(lake.delta(history.key()))
                        .where(FACT_UNIQUE_ID.eq(fact))
                        .orderBy(START_AT),
                MAX_VERSIONS);
    }

    public QueryResult arrivals(LakeTable silver, String fact, String column) {
        return serving.fetch(
                select(withColumn(column, PROCESSED_AT, RUN_ID, SOURCE_FILE, INGESTED_AT))
                        .from(lake.delta(silver.key()))
                        .where(FACT_UNIQUE_ID.eq(fact))
                        .orderBy(PROCESSED_AT),
                MAX_VERSIONS);
    }

    public QueryResult quarantined(LakeTable quarantine, String fact, String column) {
        return serving.fetch(
                select(withColumn(column, PROCESSED_AT, FAILED_EXPECTATIONS, SOURCE_FILE))
                        .from(lake.delta(quarantine.key()))
                        .where(FACT_UNIQUE_ID.eq(fact))
                        .orderBy(PROCESSED_AT),
                MAX_VERSIONS);
    }

    public QueryResult raw(LakeTable bronze, String file, Map<String, String> keyValues) {
        List<Condition> filters = new ArrayList<>();
        filters.add(SOURCE_FILE.eq(file));
        keyValues.forEach(
                (column, value) -> filters.add(text(field(name(column))).eq(value)));
        return serving.fetch(selectFrom(lake.delta(bronze.key())).where(filters).limit(MAX_RAW_ROWS), MAX_RAW_ROWS);
    }
}
