package com.dataflow.workspace.folders;

import com.dataflow.spec.InvalidPipelineSpecException;
import com.dataflow.spec.InvalidProjectSpecException;
import com.dataflow.spec.Names;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.ProjectLoader;
import com.dataflow.spec.ProjectSpec;
import com.dataflow.workspace.specs.Parsing;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@Component
public class FolderProjects {

    private final ProjectLoader loader;
    private final Parsing parsing;

    public FolderProjects(PipelineSpecLoader specs, Parsing parsing) {
        this.loader = new ProjectLoader(specs);
        this.parsing = parsing;
    }

    static String starter(String folderName) {
        String name = folderName
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("^[^a-z]+|-+$", "");
        String project = name.isEmpty() ? "project" : Names.truncated(name);
        return "project: " + project + "\n"
                + "targets:\n"
                + "  dev:\n"
                + "    mode: development\n"
                + "  prod:\n"
                + "    mode: production\n"
                + "    branch: main\n";
    }

    Optional<ProjectSpec> parse(String content) {
        try {
            return Optional.of(loader.parse(content, ProjectSpec.FILE));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    void check(Path workTree) {
        if (!Files.isRegularFile(workTree.resolve(ProjectSpec.FILE))) {
            return;
        }
        try {
            parsing.run(() -> loader.load(workTree));
        } catch (InvalidProjectSpecException | InvalidPipelineSpecException | IllegalStateException e) {
            throw new GitFolderException("The project would not load after this commit: " + e.getMessage(), e);
        }
    }
}
