package com.dataflow.spec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.StringReader;
import java.util.List;
import java.util.Optional;

final class YamlLines {

    private YamlLines() {}

    static String prefix(String content, JsonProcessingException e) {
        Optional<Integer> line =
                e instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()
                        ? lineOf(content, mapping.getPath())
                        : Optional.empty();
        if (line.isEmpty() && e.getLocation() != null) {
            line = Optional.of(e.getLocation().getLineNr());
        }
        return line.map(number -> "line " + number + ": ").orElse("");
    }

    static Optional<Integer> lineOf(String content, List<JsonMappingException.Reference> path) {
        Node node;
        try {
            node = new Yaml(new LoaderOptions()).compose(new StringReader(content));
        } catch (YAMLException e) {
            return Optional.empty();
        }
        Node at = null;
        for (JsonMappingException.Reference step : path) {
            if (step.getFieldName() != null && node instanceof MappingNode mapping) {
                NodeTuple entry = mapping.getValue().stream()
                        .filter(tuple -> tuple.getKeyNode() instanceof ScalarNode key
                                && key.getValue().equals(step.getFieldName()))
                        .findFirst()
                        .orElse(null);
                if (entry == null) {
                    return Optional.empty();
                }
                at = entry.getKeyNode();
                node = entry.getValueNode();
            } else if (step.getIndex() >= 0
                    && node instanceof SequenceNode sequence
                    && step.getIndex() < sequence.getValue().size()) {
                node = sequence.getValue().get(step.getIndex());
                at = node;
            } else {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(at).map(found -> found.getStartMark().getLine() + 1);
    }
}
