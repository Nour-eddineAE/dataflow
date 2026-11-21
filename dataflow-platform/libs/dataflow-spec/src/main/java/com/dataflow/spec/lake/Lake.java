package com.dataflow.spec.lake;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public record Lake(String root) {

    public static final Pattern KEY = Pattern.compile("(?!system/)[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");
    public static final List<String> CLEANABLE = List.of(
            "system/tasks/", "spark-events/", "system/checkpoints/", "uploads/");
    private static final String SEPARATOR = "/";
    private static final String SYSTEM = "system";
    private static final String EVENTS = "events";
    private static final String CHECKPOINTS = "checkpoints";
    private static final String TASKS = "tasks";
    private static final String JSON = ".json";
    private static final Pattern CLEANABLE_KEY = Pattern.compile("[A-Za-z0-9._=-]+(/[A-Za-z0-9._=-]+)*/?");

    public Lake(String root) {
        this.root = root.endsWith(SEPARATOR) ? root.substring(0, root.length() - 1) : root;
    }

    public static String key(Layer layer, String dataset) {
        return layer.segment() + SEPARATOR + dataset + SEPARATOR;
    }

    public static String key(Layer layer, String dataset, String name) {
        return layer.segment() + SEPARATOR + dataset + SEPARATOR + name + SEPARATOR;
    }

    public static String eventsKey(String dataset) {
        return SYSTEM + SEPARATOR + EVENTS + SEPARATOR + dataset + SEPARATOR;
    }

    public static Optional<String> eventsDataset(String key) {
        String root = SYSTEM + SEPARATOR + EVENTS + SEPARATOR;
        if (!key.startsWith(root) || !key.endsWith(SEPARATOR)) {
            return Optional.empty();
        }
        String dataset = key.substring(root.length(), key.length() - 1);
        return dataset.isEmpty() || dataset.contains(SEPARATOR) ? Optional.empty() : Optional.of(dataset);
    }

    public static String taskKey(String id) {
        return SYSTEM + SEPARATOR + TASKS + SEPARATOR + id + JSON;
    }

    public static String uploadsOf(String person) {
        return "uploads" + SEPARATOR + person.replaceAll("[^A-Za-z0-9._-]+|\\.\\.+", "_") + SEPARATOR;
    }

    public String table(Layer layer, String dataset) {
        return locate(key(layer, dataset));
    }

    public String table(Layer layer, String dataset, String name) {
        return locate(key(layer, dataset, name));
    }

    public String events(String dataset) {
        return locate(eventsKey(dataset));
    }

    public String file(String keyOrUri) {
        String trimmed = keyOrUri.strip();
        String prefix = root + "/";
        String key = trimmed.startsWith(prefix) ? trimmed.substring(prefix.length()) : trimmed;
        if (!KEY.matcher(key).matches() || key.contains("..")) {
            throw new IllegalArgumentException("Not a file of the lake: " + keyOrUri);
        }
        return prefix + key;
    }

    public String cleanable(String key) {
        String trimmed = key.strip();
        boolean allowed =
                CLEANABLE.stream().anyMatch(prefix -> trimmed.startsWith(prefix) && trimmed.length() > prefix.length());
        if (!allowed
                || trimmed.contains("..")
                || !CLEANABLE_KEY.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Not a location housekeeping may remove: " + key);
        }
        return root + SEPARATOR + trimmed;
    }

    public String task(String id) {
        return locate(taskKey(id));
    }

    public String checkpoint(String dataset, String flow) {
        return locate(SYSTEM + SEPARATOR + CHECKPOINTS + SEPARATOR + dataset + SEPARATOR + flow + SEPARATOR);
    }

    private String locate(String key) {
        return root + SEPARATOR + key;
    }
}
