package com.dataflow.spec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public record ComputeSpec(
        Executors executors,
        String driverMemory,
        String executorMemory,
        Integer driverCores,
        Integer executorCores,
        Map<String, String> sparkConf) {

    public static final ComputeSpec DEFAULT = new ComputeSpec(null, null, null, null, null, null);

    private static final Pattern MEMORY = Pattern.compile("[1-9][0-9]*[mg]", Pattern.CASE_INSENSITIVE);
    private static final Pattern BYTES = Pattern.compile("-1|[0-9]+(?:b|k|kb|m|mb|g|gb)?", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Predicate<String>> SETTINGS = Map.of(
            "spark.sql.shuffle.partitions", ComputeSpec::positive,
            "spark.default.parallelism", ComputeSpec::positive,
            "spark.sql.adaptive.enabled", ComputeSpec::bool,
            "spark.sql.adaptive.coalescePartitions.enabled", ComputeSpec::bool,
            "spark.sql.adaptive.skewJoin.enabled", ComputeSpec::bool,
            "spark.sql.optimizer.dynamicPartitionPruning.enabled", ComputeSpec::bool,
            "spark.sql.parquet.filterPushdown", ComputeSpec::bool,
            "spark.eventLog.enabled", ComputeSpec::bool,
            "spark.sql.autoBroadcastJoinThreshold",
                    value -> BYTES.matcher(value).matches());

    public ComputeSpec {
        sparkConf = sparkConf == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(sparkConf));
    }

    private static void memory(List<String> problems, String name, String value) {
        if (value != null && !MEMORY.matcher(value).matches()) {
            problems.add("compute." + name + " must be a positive size such as 512m or 4g");
        }
    }

    private static void cores(List<String> problems, String name, Integer value) {
        if (value != null && value < 1) {
            problems.add("compute." + name + " must be 1 or more");
        }
    }

    private static boolean bool(String value) {
        return value.equals("true") || value.equals("false");
    }

    private static boolean positive(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (executors != null) {
            if (executors.min() != null && executors.min() < 0) {
                problems.add("compute.executors.min must be 0 or more");
            }
            if (executors.max() != null && executors.max() < 1) {
                problems.add("compute.executors.max must be 1 or more");
            }
            if (executors.min() != null && executors.max() != null && executors.min() > executors.max()) {
                problems.add("compute.executors.min must not exceed max");
            }
        }
        memory(problems, "driver-memory", driverMemory);
        memory(problems, "executor-memory", executorMemory);
        cores(problems, "driver-cores", driverCores);
        cores(problems, "executor-cores", executorCores);
        sparkConf.forEach((key, value) -> {
            Predicate<String> check = SETTINGS.get(key);
            if (check == null) {
                problems.add("compute.spark-conf setting is not allowed: " + key);
            } else if (value == null || !check.test(value)) {
                problems.add("compute.spark-conf has an invalid value for " + key);
            }
        });
        return List.copyOf(problems);
    }

    public record Executors(Integer min, Integer max) {}
}
