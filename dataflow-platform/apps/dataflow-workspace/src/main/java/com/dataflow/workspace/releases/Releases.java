package com.dataflow.workspace.releases;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.Project;
import com.dataflow.spec.ProjectSpec;
import com.dataflow.spec.ReleaseId;
import com.dataflow.spec.TargetSpec;
import com.dataflow.workspace.access.DraftAccess;
import com.dataflow.workspace.folders.Branches;
import com.dataflow.workspace.folders.FolderViews.Snapshot;
import com.dataflow.workspace.folders.GitFolderException;
import com.dataflow.workspace.folders.FolderSync;
import com.dataflow.workspace.folders.Person;
import com.dataflow.workspace.runs.Tower;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

@Slf4j
@Service
public class Releases {

    private static final List<String> RELEASED_FOLDERS =
            List.of(ProjectSpec.PIPELINES + "/", "transformations/", "dashboards/");

    private final FolderSync sync;
    private final ProjectSnapshots projects;
    private final ReleaseWriter writer;
    private final DeployedSpecs deployed;
    private final Tower tower;
    private final ApplicationEventPublisher events;
    private final DraftAccess drafts;

    public Releases(
            FolderSync sync,
            ProjectSnapshots projects,
            ReleaseWriter writer,
            DeployedSpecs deployed,
            Tower tower,
            ApplicationEventPublisher events,
            DraftAccess drafts) {
        this.sync = sync;
        this.projects = projects;
        this.writer = writer;
        this.deployed = deployed;
        this.tower = tower;
        this.events = events;
        this.drafts = drafts;
    }

    private static boolean released(String path) {
        return path.equals(ProjectSpec.FILE) || RELEASED_FOLDERS.stream().anyMatch(path::startsWith);
    }

    public ReleaseView release(UUID folderId, String target, Person person) {
        Snapshot local = sync.snapshot(folderId, Constants.HEAD, Releases::released);
        TargetSpec spec = projects.load(local)
                .spec()
                .target(target)
                .orElseThrow(() -> new IllegalArgumentException(
                        "The project in " + local.folder().path() + " has no target '" + target + "'"));
        Snapshot snapshot = spec.production()
                ? sync.snapshot(folderId, Branches.remote(spec.branch()), Releases::released)
                : local;
        Project project = projects.load(snapshot);
        project.spec()
                .target(target)
                .orElseThrow(() -> new GitFolderException(
                        "The project at " + snapshot.commit() + " has no target '" + target + "'"));
        projects.requireReleasable(snapshot, project);
        Set<String> datasets = new TreeSet<>(
                project.pipelines().all().stream().map(PipelineSpec::dataset).toList());
        project.pipelines().all().forEach(pipeline -> drafts.require(pipeline, datasets::contains));
        if (target.equals(deployed.target())) {
            checkClashes(project.name(), datasets);
        }
        ReleaseId id = new ReleaseId(project.name(), snapshot.commit());
        Instant now = writer.write(id, target, snapshot, folderId, person);
        log.info("Released {} to {} from {} ({} datasets)", id, target, local.folder().path(), datasets.size());
        if (target.equals(deployed.target())) {
            deployed.refresh();
            tower.reload();
            events.publishEvent(new Released(id.project(), target, person.id(), owners(project)));
        }
        return new ReleaseView(id.project(), target, id.commit(), now, person.id(), List.copyOf(datasets));
    }

    private static Map<String, String> owners(Project project) {
        Map<String, String> owners = new TreeMap<>();
        project.pipelines().all().stream()
                .filter(spec -> spec.owner() != null && !spec.owner().isBlank())
                .forEach(spec -> owners.put(spec.dataset(), spec.owner()));
        return owners;
    }

    public List<ReleaseView> current() {
        return deployed.releases().stream()
                .map(pointer -> new ReleaseView(
                        pointer.project(),
                        pointer.target(),
                        pointer.commit(),
                        pointer.releasedAt(),
                        pointer.releasedBy(),
                        deployed.datasets().entrySet().stream()
                                .filter(entry -> entry.getValue().equals(pointer.id()))
                                .map(Map.Entry::getKey)
                                .toList()))
                .toList();
    }

    private void checkClashes(String project, Set<String> datasets) {
        deployed.datasets().forEach((dataset, release) -> {
            if (datasets.contains(dataset) && !release.project().equals(project)) {
                throw new GitFolderException(
                        "Dataset " + dataset + " is already released by project " + release.project());
            }
        });
    }

    public record ReleaseView(
            String project,
            String target,
            String commit,
            Instant releasedAt,
            String releasedBy,
            List<String> datasets) {}
}
