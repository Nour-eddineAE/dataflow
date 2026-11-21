package com.dataflow.spec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class PipelineSpecLoader {

    public static final String PLAN_FILE = "pipeline.yml";

    private static final String EMPTY = "the spec is empty";
    private static final TypeReference<LinkedHashMap<String, Object>> TREE = new TypeReference<>() {};

    private final ObjectMapper yaml = SpecYaml.mapper();

    private static List<Path> planFiles(Path root) {
        try (Stream<Path> folders = Files.list(root)) {
            return folders.filter(Files::isDirectory)
                    .map(folder -> folder.resolve(PLAN_FILE))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list pipeline specs under " + root.toAbsolutePath(), e);
        }
    }

    public PipelineSpecs loadAll(Path root) {
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Pipeline spec directory " + root.toAbsolutePath() + " does not exist");
        }
        PipelineSpecs plans = loadFolder(root);
        if (plans.all().isEmpty()) {
            throw new IllegalStateException("No " + PLAN_FILE + " found under " + root.toAbsolutePath());
        }
        return plans;
    }

    public PipelineSpecs loadFolder(Path root) {
        Map<String, PipelineSpec> plans = new TreeMap<>();
        if (!Files.isDirectory(root)) {
            return new PipelineSpecs(plans);
        }
        for (Path file : planFiles(root)) {
            PipelineSpec plan = load(file);
            String folder = file.getParent().getFileName().toString();
            if (!folder.equals(plan.dataset())) {
                throw new InvalidPipelineSpecException(
                        file.toString(),
                        List.of("dataset '" + plan.dataset() + "' must match its folder name '" + folder + "'"));
            }
            plans.put(plan.dataset(), plan);
        }
        return new PipelineSpecs(plans);
    }

    public PipelineSpec load(Path file) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new InvalidPipelineSpecException(file.toString(), e.getMessage(), e);
        }
        return parse(content, file.toString());
    }

    public PipelineSpec parse(String content, String source) {
        if (content == null || content.isBlank()) {
            throw new InvalidPipelineSpecException(source, List.of(EMPTY));
        }
        PipelineSpec plan;
        try {
            plan = yaml.readValue(content, PipelineSpec.class);
        } catch (JsonProcessingException e) {
            throw new InvalidPipelineSpecException(source, YamlLines.prefix(content, e) + SpecYaml.reason(e), e);
        }
        if (plan == null) {
            throw new InvalidPipelineSpecException(source, List.of(EMPTY));
        }
        List<String> problems = PipelineSpecValidator.validate(plan);
        if (!problems.isEmpty()) {
            throw new InvalidPipelineSpecException(source, problems);
        }
        return plan;
    }

    public Map<String, Object> tree(String content, String source) {
        if (content == null || content.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> tree = yaml.readValue(content, TREE);
            return tree == null ? new LinkedHashMap<>() : tree;
        } catch (JsonProcessingException e) {
            throw new InvalidPipelineSpecException(source, YamlLines.prefix(content, e) + SpecYaml.reason(e), e);
        }
    }

    public ComputeSpec compute(String content, String source) {
        try {
            JsonNode compute = yaml.readTree(content == null ? "" : content).path("compute");
            return compute.isObject() ? yaml.treeToValue(compute, ComputeSpec.class) : ComputeSpec.DEFAULT;
        } catch (JsonProcessingException e) {
            throw new InvalidPipelineSpecException(source, YamlLines.prefix(content, e) + SpecYaml.reason(e), e);
        }
    }
}
