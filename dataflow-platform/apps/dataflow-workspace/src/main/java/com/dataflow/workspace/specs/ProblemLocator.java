package com.dataflow.workspace.specs;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProblemLocator {

    private static final Pattern LINE = Pattern.compile("^line (\\d+): ");
    private static final Pattern NAMED =
            Pattern.compile("^(?:silver\\.columns|silver\\.derive|expectation|gold|reference)[ .']*([A-Za-z0-9_]+)");
    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");
    private static final Pattern YAML_MARK = Pattern.compile("in 'reader', line (\\d+), column \\d+");
    private static final List<String> SECTIONS = List.of(
            "dataset", "version", "schedule", "source", "bronze", "silver", "history", "references", "gold", "serve");

    private ProblemLocator() {}

    public static Problem locate(String content, String message) {
        Matcher mark = YAML_MARK.matcher(message);
        if (mark.find()) {
            return new Problem(yamlProblem(message), Integer.parseInt(mark.group(1)));
        }
        Matcher line = LINE.matcher(message);
        if (line.find()) {
            return new Problem(message.substring(line.end()), Integer.parseInt(line.group(1)));
        }
        List<String> lines = content.lines().toList();
        Matcher named = NAMED.matcher(message);
        if (named.find()) {
            Integer at = find(lines, "name: " + named.group(1));
            if (at != null) {
                return new Problem(message, at);
            }
        }
        if (message.startsWith("history key") || message.startsWith("serve")) {
            Matcher quoted = QUOTED.matcher(message);
            Integer at = quoted.find() ? find(lines, quoted.group(1)) : null;
            if (at != null) {
                return new Problem(message, at);
            }
        }
        for (String section : SECTIONS) {
            if (message.startsWith(section)) {
                return new Problem(message, find(lines, section + ":"));
            }
        }
        return new Problem(message, null);
    }

    private static String yamlProblem(String message) {
        List<String> said = message.lines()
                .filter(text -> !text.isBlank() && !Character.isWhitespace(text.charAt(0)))
                .map(text -> LINE.matcher(text).replaceFirst(""))
                .toList();
        return said.isEmpty() ? message.strip() : said.getLast();
    }

    private static Integer find(List<String> lines, String text) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(text)) {
                return i + 1;
            }
        }
        return null;
    }
}
