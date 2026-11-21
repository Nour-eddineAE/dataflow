package com.dataflow.workspace.compute;

import com.dataflow.spec.ComputePolicy;
import com.dataflow.spec.ComputeSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.ProjectLoader;
import com.dataflow.spec.ProjectSpec;
import com.dataflow.spec.TargetSpec;
import com.dataflow.workspace.folders.FolderFiles;
import com.dataflow.workspace.folders.FolderSync;
import com.dataflow.workspace.folders.FolderViews.CommitView;
import com.dataflow.workspace.folders.FolderViews.FolderSummary;
import com.dataflow.workspace.folders.GitFolderException;
import com.dataflow.workspace.folders.GitFolders;
import com.dataflow.workspace.folders.Person;
import com.dataflow.workspace.releases.DeployedSpecs;
import com.dataflow.workspace.releases.ReleaseFiles.Pointer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class ProjectCompute {

    private static final String UNREADABLE = "Could not read the project: ";
    private static final int SHORT_COMMIT = 7;

    private final GitFolders folders;
    private final FolderFiles files;
    private final FolderSync sync;
    private final DeployedSpecs deployed;
    private final PipelineSpecLoader pipelines;
    private final ProjectLoader loader;
    private final ComputePolicy policy;

    public ProjectCompute(
            GitFolders folders,
            FolderFiles files,
            FolderSync sync,
            DeployedSpecs deployed,
            PipelineSpecLoader pipelines,
            ComputePolicy policy) {
        this.folders = folders;
        this.files = files;
        this.sync = sync;
        this.deployed = deployed;
        this.pipelines = pipelines;
        this.loader = new ProjectLoader(pipelines);
        this.policy = policy;
    }

    public ComputePolicy policy() {
        return policy;
    }

    public List<ProjectView> projects(Function<FolderSummary, Rights> rights) {
        return folders.list().stream()
                .filter(folder -> folder.project() != null)
                .filter(folder -> rights.apply(folder).read())
                .flatMap(folder -> view(folder, rights.apply(folder)).stream())
                .toList();
    }

    public ProjectView update(
            UUID folderId, String target, ComputeSpec compute, Person person, Function<FolderSummary, Rights> rights) {
        List<String> violations = compute == null ? List.of() : policy.violations(compute);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", violations));
        }
        String content = committed(folderId)
                .orElseThrow(() -> new IllegalArgumentException("The folder has no committed " + ProjectSpec.FILE));
        String changed = loader.withCompute(content, target, compute == null ? ComputeSpec.DEFAULT : compute);
        CommitView commit = sync.commitFile(
                folderId, ProjectSpec.FILE, changed, "chore(compute): size the " + target + " runs", person);
        try {
            sync.push(folderId);
        } catch (RuntimeException e) {
            throw new GitFolderException("Committed " + commit.id().substring(0, SHORT_COMMIT)
                    + " but the push failed, so a release cannot use it yet: " + e.getMessage(), e);
        }
        FolderSummary folder = folders.list().stream()
                .filter(summary -> summary.id().equals(folderId))
                .findFirst()
                .orElseThrow();
        return view(folder, rights.apply(folder)).orElseThrow();
    }

    private Optional<String> committed(UUID folderId) {
        try {
            return Optional.ofNullable(files.read(folderId, ProjectSpec.FILE).committed());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<ProjectView> view(FolderSummary folder, Rights rights) {
        try {
            Optional<ProjectSpec> spec = committed(folder.id()).flatMap(this::parse);
            if (spec.isEmpty()) {
                return Optional.empty();
            }
            CommitView head = sync.log(folder.id()).stream().findFirst().orElse(null);
            List<TargetView> targets = spec.get().targets().entrySet().stream()
                    .map(entry -> target(spec.get().project(), entry.getKey(), entry.getValue()))
                    .toList();
            return Optional.of(new ProjectView(
                    folder.id(), folder.path(), spec.get().project(), head, rights, targets, null));
        } catch (RuntimeException e) {
            log.warn("Compute of {} not read", folder.path(), e);
            return Optional.of(new ProjectView(
                    folder.id(), folder.path(), folder.project(), null, rights, List.of(), UNREADABLE + e.getMessage()));
        }
    }

    private TargetView target(String project, String name, TargetSpec target) {
        Optional<Pointer> pointer = deployed.pointer(project, name);
        return new TargetView(
                name,
                target.production(),
                target.branch(),
                target.compute(),
                pointer.map(Pointer::commit).orElse(null),
                pointer.map(Pointer::releasedAt).orElse(null),
                pointer.map(Pointer::releasedBy).orElse(null),
                name.equals(deployed.target()) ? ownCompute(project) : List.of());
    }

    private List<PipelineCompute> ownCompute(String project) {
        return deployed.datasets().entrySet().stream()
                .filter(entry -> entry.getValue().project().equals(project))
                .map(Map.Entry::getKey)
                .sorted()
                .flatMap(dataset -> deployed.releasedFile(dataset, released(dataset))
                        .map(content -> new PipelineCompute(dataset, pipelines.compute(content, dataset)))
                        .stream())
                .filter(pipeline -> !pipeline.compute().equals(ComputeSpec.DEFAULT))
                .toList();
    }

    private static String released(String dataset) {
        return ProjectSpec.PIPELINES + "/" + dataset + "/" + PipelineSpecLoader.PLAN_FILE;
    }

    private Optional<ProjectSpec> parse(String content) {
        try {
            return Optional.of(loader.parse(content, ProjectSpec.FILE));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public record Rights(boolean read, boolean edit, boolean release) {}

    public record ProjectView(
            UUID folderId,
            String folder,
            String project,
            CommitView head,
            Rights rights,
            List<TargetView> targets,
            String problem) {}

    public record TargetView(
            String name,
            boolean production,
            String branch,
            ComputeSpec compute,
            String releasedCommit,
            Instant releasedAt,
            String releasedBy,
            List<PipelineCompute> pipelines) {}

    public record PipelineCompute(String dataset, ComputeSpec compute) {}
}
