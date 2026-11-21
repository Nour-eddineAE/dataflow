package com.dataflow.workspace.releases;

import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.InvalidPipelineSpecException;
import com.dataflow.spec.InvalidProjectSpecException;
import com.dataflow.spec.PipelineLinks;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.Project;
import com.dataflow.spec.ProjectLoader;
import com.dataflow.spec.ProjectSpec;
import com.dataflow.workspace.folders.FolderViews.Snapshot;
import com.dataflow.workspace.folders.GitFolderException;
import com.dataflow.workspace.folders.JavaChecks;
import com.dataflow.workspace.specs.Parsing;
import com.dataflow.workspace.specs.Problem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

@Slf4j
@Component
public class ProjectSnapshots {

    private final JavaChecks java;
    private final ProjectLoader projects;
    private final Parsing parsing;

    public ProjectSnapshots(JavaChecks java, PipelineSpecLoader specs, Parsing parsing) {
        this.java = java;
        this.projects = new ProjectLoader(specs);
        this.parsing = parsing;
    }

    private static void delete(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot delete " + path, e);
                }
            });
        } catch (UncheckedIOException | IOException e) {
            log.warn("Cannot delete {}: {}", dir, e.getMessage());
        }
    }

    private static void checkLinks(Snapshot snapshot, Project project) {
        List<String> problems = PipelineLinks.problems(project.pipelines());
        if (!problems.isEmpty()) {
            throw new GitFolderException(
                    "Pipelines of " + snapshot.commit() + " read tables that do not exist",
                    problems.stream()
                            .map(problem -> new Problem(problem, null))
                            .toList(),
                    null);
        }
    }

    public Project load(Snapshot snapshot) {
        if (!snapshot.files().containsKey(ProjectSpec.FILE)) {
            throw new GitFolderException(
                    "Commit " + snapshot.commit() + " has no " + ProjectSpec.FILE + " at its root");
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("release-check-");
            for (Map.Entry<String, byte[]> file : snapshot.files().entrySet()) {
                Path target = dir.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.write(target, file.getValue());
            }
            Path root = dir;
            return parsing.run(() -> projects.load(root));
        } catch (InvalidProjectSpecException | InvalidPipelineSpecException | IllegalStateException e) {
            throw new GitFolderException("Commit " + snapshot.commit() + " cannot be released: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            delete(dir);
        }
    }

    public void requireReleasable(Snapshot snapshot, Project project) {
        checkTransformations(snapshot, project);
        checkLinks(snapshot, project);
    }

    private void checkTransformations(Snapshot snapshot, Project project) {
        Map<String, String> sources = new TreeMap<>();
        snapshot.files().forEach((path, content) -> {
            if (JavaChecks.applies(path)) {
                sources.put(path, new String(content, StandardCharsets.UTF_8));
            }
        });
        JavaChecks.Result compiled = java.compile(sources);
        if (!compiled.problems().isEmpty()) {
            throw new GitFolderException(
                    "Transformations of " + snapshot.commit() + " do not compile",
                    compiled.problems().stream()
                            .map(located -> new Problem(
                                    (located.path() == null ? "" : located.path() + ": ")
                                            + located.problem().message(),
                                    located.problem().line()))
                            .toList(),
                    null);
        }
        for (PipelineSpec spec : project.pipelines().all()) {
            Stream.concat(
                            spec.silver().steps().stream(),
                            spec.gold().stream()
                                    .filter(GoldSpec::usesTransformation)
                                    .map(GoldSpec::asStep))
                    .filter(step -> !compiled.ids().contains(step.transformation()))
                    .findFirst()
                    .ifPresent(step -> {
                        throw new GitFolderException(spec.dataset() + " step " + step.name() + " uses "
                                + step.transformation() + ", which no transformation of " + snapshot.commit()
                                + " declares; found " + compiled.ids());
                    });
        }
    }
}
