package com.dataflow.spec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class BoardSpecLoader {

    private static final Pattern CHART = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)?/[A-Za-z][A-Za-z0-9_-]*");

    private final ObjectMapper yaml = SpecYaml.mapper();

    private static void check(String key, List<String> charts, List<String> problems) {
        for (String chart : charts) {
            if (chart == null || !CHART.matcher(chart).matches()) {
                problems.add(key + ": '" + chart
                        + "' is not <table>/<chart>, such as current/time or gold.daily_activity/bars-eventType");
            }
        }
    }

    public BoardSpec parse(String content, String source) {
        if (content == null || content.isBlank()) {
            return BoardSpec.NONE;
        }
        BoardSpec spec;
        try {
            spec = yaml.readValue(content, BoardSpec.class);
        } catch (JsonProcessingException e) {
            throw new InvalidProjectSpecException(source, YamlLines.prefix(content, e) + SpecYaml.reason(e), e);
        }
        if (spec == null) {
            return BoardSpec.NONE;
        }
        List<String> problems = new ArrayList<>();
        check("hide", spec.hide(), problems);
        check("first", spec.first(), problems);
        if (!problems.isEmpty()) {
            throw new InvalidProjectSpecException(source, problems);
        }
        return spec;
    }
}
