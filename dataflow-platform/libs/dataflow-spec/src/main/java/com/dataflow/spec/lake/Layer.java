package com.dataflow.spec.lake;

import java.util.Locale;

public enum Layer {
    LANDING,
    BRONZE,
    SILVER,
    QUARANTINE,
    HISTORY,
    REFERENCE,
    GOLD;

    public String segment() {
        return name().toLowerCase(Locale.ROOT);
    }
}
