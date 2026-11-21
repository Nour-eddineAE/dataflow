package com.dataflow.workspace.catalog;

import com.dataflow.spec.SystemColumns;
import org.jooq.Field;

import static com.dataflow.workspace.serving.ClickHouseDsl.column;

public final class LakeFields {

    public static final Field<String> FACT_UNIQUE_ID = column(SystemColumns.FACT_UNIQUE_ID, String.class);
    public static final Field<String> PROCESSED_AT = column(SystemColumns.PROCESSED_AT, String.class);
    public static final Field<String> CONTENT_HASH = column(SystemColumns.CONTENT_HASH, String.class);
    public static final Field<String> FAILED_EXPECTATIONS = column(SystemColumns.FAILED_EXPECTATIONS, String.class);
    public static final Field<String> START_AT = column(SystemColumns.START_AT, String.class);
    public static final Field<String> END_AT = column(SystemColumns.END_AT, String.class);
    public static final Field<String> SOURCE_FILE = column(SystemColumns.SOURCE_FILE, String.class);
    public static final Field<String> INGESTED_AT = column(SystemColumns.INGESTED_AT, String.class);
    public static final Field<String> RUN_ID = column(SystemColumns.RUN_ID, String.class);

    public static final Field<Long> OBJECT_SIZE = column("_size", Long.class);

    private LakeFields() {}
}
