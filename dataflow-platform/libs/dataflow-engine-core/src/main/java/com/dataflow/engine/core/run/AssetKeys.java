package com.dataflow.engine.core.run;

import com.dataflow.spec.lake.Layer;

public final class AssetKeys {

    private AssetKeys() {}

    public static String of(String dataset, Layer layer) {
        return of(dataset, layer.segment());
    }

    public static String of(String dataset, Layer layer, String name) {
        return of(dataset, layer.segment(), name);
    }

    public static String of(String dataset, String... path) {
        return dataset + "/" + String.join("/", path);
    }
}
