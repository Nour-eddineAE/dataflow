package com.dataflow.spec.lake;

import com.dataflow.spec.ServeSpec;

public final class FlowNames {

    public static final String INGEST = "ingest";
    public static final String REFERENCES = "references";
    public static final String BRONZE = Layer.BRONZE.segment();
    public static final String SILVER = Layer.SILVER.segment();
    public static final String HISTORY = Layer.HISTORY.segment();
    public static final String SERVING = "serving";
    public static final String MAINTENANCE = "maintenance";

    private FlowNames() {}

    public static String gold(String name) {
        return ServeSpec.GOLD_PREFIX + name;
    }
}
