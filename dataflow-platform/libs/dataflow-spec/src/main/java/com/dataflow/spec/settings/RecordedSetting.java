package com.dataflow.spec.settings;

public record RecordedSetting(String name, String type, String description, String value) {

    public static final String RUN_DETAIL = "settings";
    public static final String METADATA = "META-INF/spring-configuration-metadata.json";
}
