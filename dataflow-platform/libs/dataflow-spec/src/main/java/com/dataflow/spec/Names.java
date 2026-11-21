package com.dataflow.spec;

import java.util.regex.Pattern;

public final class Names {

    public static final int MAX_LENGTH = 63;
    public static final String PLATFORM = "platform";
    public static final Pattern DATASET = Pattern.compile(pattern("[a-z0-9_]"));

    private Names() {}

    public static String pattern(String characters) {
        return "[a-z]" + characters + "{0," + (MAX_LENGTH - 1) + "}";
    }

    public static String truncated(String name) {
        return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name;
    }
}
