package com.dataflow.spec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ProjectLoader {

    private static final String EMPTY = "the project file is empty";
    private static final String TARGETS = "targets";
    private static final String COMPUTE = "compute";

    private final ObjectMapper yaml = SpecYaml.mapper();
    private final ObjectMapper writer = SpecYaml.writer();
    private final PipelineSpecLoader pipelines;

    public ProjectLoader(PipelineSpecLoader pipelines) {
        this.pipelines = pipelines;
    }

    public Project load(Path root) {
        Path file = root.resolve(ProjectSpec.FILE);
        if (!Files.isRegularFile(file)) {
            throw new InvalidProjectSpecException(
                    root.toString(), List.of("no " + ProjectSpec.FILE + " at the project root"));
        }
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new InvalidProjectSpecException(file.toString(), e.getMessage(), e);
        }
        ProjectSpec spec = parse(content, file.toString());
        return new Project(root, spec, pipelines.loadFolder(root.resolve(ProjectSpec.PIPELINES)));
    }

    public ProjectSpec parse(String content, String source) {
        if (content == null || content.isBlank()) {
            throw new InvalidProjectSpecException(source, List.of(EMPTY));
        }
        ProjectSpec spec;
        try {
            spec = yaml.readValue(content, ProjectSpec.class);
        } catch (JsonProcessingException e) {
            throw new InvalidProjectSpecException(source, YamlLines.prefix(content, e) + SpecYaml.reason(e), e);
        }
        if (spec == null) {
            throw new InvalidProjectSpecException(source, List.of(EMPTY));
        }
        List<String> problems = ProjectSpecValidator.validate(spec);
        if (!problems.isEmpty()) {
            throw new InvalidProjectSpecException(source, problems);
        }
        return spec;
    }

    public String withCompute(String content, String target, ComputeSpec compute) {
        if (parse(content, ProjectSpec.FILE).target(target).isEmpty()) {
            throw new IllegalArgumentException("The project has no target '" + target + "'");
        }
        String changed;
        try {
            ObjectNode document = (ObjectNode) yaml.readTree(content);
            ObjectNode node = (ObjectNode) document.path(TARGETS).path(target);
            ObjectNode sized = writer.valueToTree(compute);
            if (sized.isEmpty()) {
                node.remove(COMPUTE);
            } else {
                node.set(COMPUTE, sized);
            }
            changed = writer.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new InvalidProjectSpecException(ProjectSpec.FILE, e.getOriginalMessage(), e);
        }
        parse(changed, ProjectSpec.FILE);
        return changed;
    }
}
