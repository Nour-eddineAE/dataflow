package com.dataflow.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record ComputePolicy(
        String maxDriverMemory,
        Integer maxDriverCores,
        String maxExecutorMemory,
        Integer maxExecutorCores,
        Integer maxExecutors) {

    public static final ComputePolicy NONE = new ComputePolicy(null, null, null, null, null);

    private static final Pattern SIZE = Pattern.compile("(?<amount>[1-9][0-9]*)(?<unit>[mg])", Pattern.CASE_INSENSITIVE);
    private static final long MIB_PER_GIB = 1024;

    public ComputePolicy {
        maxDriverMemory = blankToNull(maxDriverMemory);
        maxExecutorMemory = blankToNull(maxExecutorMemory);
        mebibytes(maxDriverMemory, "max-driver-memory");
        mebibytes(maxExecutorMemory, "max-executor-memory");
        positive(maxDriverCores, "max-driver-cores");
        positive(maxExecutorCores, "max-executor-cores");
        positive(maxExecutors, "max-executors");
    }

    public boolean limits() {
        return !equals(NONE);
    }

    public List<String> violations(ComputeSpec compute) {
        List<String> violations = new ArrayList<>();
        memory(violations, "driver-memory", compute.driverMemory(), maxDriverMemory);
        memory(violations, "executor-memory", compute.executorMemory(), maxExecutorMemory);
        count(violations, "driver-cores", compute.driverCores(), maxDriverCores);
        count(violations, "executor-cores", compute.executorCores(), maxExecutorCores);
        if (compute.executors() != null) {
            count(violations, "executors.min", compute.executors().min(), maxExecutors);
            count(violations, "executors.max", compute.executors().max(), maxExecutors);
        }
        return List.copyOf(violations);
    }

    public static long mebibytes(String size) {
        var matcher = SIZE.matcher(size);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a size such as 512m or 4g: " + size);
        }
        long amount = Long.parseLong(matcher.group("amount"));
        return matcher.group("unit").equalsIgnoreCase("g") ? amount * MIB_PER_GIB : amount;
    }

    private static void memory(List<String> violations, String name, String value, String limit) {
        if (value != null && limit != null && SIZE.matcher(value).matches() && mebibytes(value) > mebibytes(limit)) {
            violations.add(aboveLimit(name, value, limit));
        }
    }

    private static void count(List<String> violations, String name, Integer value, Integer limit) {
        if (value != null && limit != null && value > limit) {
            violations.add(aboveLimit(name, value, limit));
        }
    }

    private static String aboveLimit(String name, Object value, Object limit) {
        return "compute." + name + " " + value + " is above the install's limit of " + limit;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void mebibytes(String value, String name) {
        if (value != null && !SIZE.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a size such as 512m or 4g, not " + value);
        }
    }

    private static void positive(Integer value, String name) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException(name + " must be 1 or more, not " + value);
        }
    }
}
