package com.dataflow.spec;

import java.util.Set;

public final class SystemColumns {

    public static final String PREFIX = "_";

    public static final String RESCUED_DATA = "_rescued_data";
    public static final String SOURCE_FILE = "_source_file";
    public static final String INGESTED_AT = "_ingested_at";
    public static final String RUN_ID = "_run_id";

    public static final String FACT_UNIQUE_ID = "factUniqueId";
    public static final String PROCESSED_AT = "processedAt";
    public static final String CONTENT_HASH = "contentHash";
    public static final String FAILED_EXPECTATIONS = "failedExpectations";
    public static final String COMPUTED_AT = "computedAt";
    public static final String START_AT = "__START_AT";
    public static final String END_AT = "__END_AT";

    public static final Set<String> RESERVED =
            Set.of(FACT_UNIQUE_ID, PROCESSED_AT, CONTENT_HASH, FAILED_EXPECTATIONS, COMPUTED_AT);

    private SystemColumns() {}

    public static boolean reserved(String column) {
        return column.startsWith(PREFIX) || RESERVED.contains(column);
    }
}
