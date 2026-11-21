package com.dataflow.workspace.folders;

import com.dataflow.spec.ProjectSpec;
import com.dataflow.workspace.folders.FolderViews.FileContent;
import com.dataflow.workspace.folders.FolderViews.FileState;
import com.dataflow.workspace.folders.FolderViews.FolderFile;
import com.dataflow.workspace.folders.FolderViews.FolderTree;
import com.dataflow.workspace.specs.Problem;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FolderFiles {

    private final GitFolders folders;
    private final FolderStore store;
    private final GitCache cache;
    private final FileChecks checks;
    private final JavaChecks java;
    private final FolderProjects projects;
    private final Clock clock;

    public FolderFiles(
            GitFolders folders,
            FolderStore store,
            GitCache cache,
            FileChecks checks,
            JavaChecks java,
            FolderProjects projects,
            Clock clock) {
        this.folders = folders;
        this.store = store;
        this.cache = cache;
        this.checks = checks;
        this.java = java;
        this.projects = projects;
        this.clock = clock;
    }

    private static FileState state(boolean committed, Draft draft) {
        if (draft == null) {
            return FileState.UNCHANGED;
        }
        if (draft.deleted()) {
            return FileState.DELETED;
        }
        return committed ? FileState.MODIFIED : FileState.ADDED;
    }

    public FolderTree tree(UUID id) {
        GitFolder folder = folders.folder(id);
        return cache.with(folder, clone -> tree(folder, clone));
    }

    public FileContent read(UUID id, String path) {
        GitFolder folder = folders.folder(id);
        String file = FilePaths.check(path);
        Draft draft = drafts(folder).get(file);
        String committed = cache.with(folder, clone -> clone.file(file).orElse(null));
        if (draft == null && committed == null) {
            throw new IllegalArgumentException("No file " + file + " in " + folder.path());
        }
        String content = draft == null ? committed : draft.content();
        return new FileContent(
                file, content, committed, state(committed != null, draft), problems(folder, file, content));
    }

    public FileContent save(UUID id, String path, String content, Person person) {
        GitFolder folder = folders.folder(id);
        String file = FilePaths.check(path);
        if (content == null) {
            throw new IllegalArgumentException("Saving needs the file's content");
        }
        keep(folder, file, content, person);
        return read(id, file);
    }

    public void remove(UUID id, String path, Person person) {
        keep(folders.folder(id), FilePaths.check(path), null, person);
    }

    public void discard(UUID id, String path) {
        GitFolder folder = folders.folder(id);
        store.deleteDrafts(folder.id(), folder.branch(), List.of(FilePaths.check(path)));
    }

    public Map<String, String> transformations(UUID id) {
        return Collections.unmodifiableMap(transformations(folders.folder(id)));
    }

    FolderTree tree(GitFolder folder, Clone clone) throws IOException {
        Optional<RevCommit> head = clone.head();
        Map<String, Draft> drafts = drafts(folder);
        Map<String, FolderFile> files = new TreeMap<>();
        if (head.isPresent()) {
            for (String path : clone.paths(head.get())) {
                files.put(path, new FolderFile(path, state(true, drafts.get(path))));
            }
        }
        drafts.forEach((path, draft) -> {
            if (!files.containsKey(path) && !draft.deleted()) {
                files.put(path, new FolderFile(path, FileState.ADDED));
            }
        });
        BranchTrackingStatus tracking = BranchTrackingStatus.of(clone.repository(), folder.branch());
        Integer ahead = tracking != null
                ? Integer.valueOf(tracking.getAheadCount())
                : head.isEmpty() ? null : clone.unpushed(head.get());
        Optional<ProjectSpec> project = Optional.ofNullable(drafts.get(ProjectSpec.FILE))
                .map(Draft::content)
                .or(() -> head.flatMap(commit -> clone.file(commit, ProjectSpec.FILE)))
                .flatMap(projects::parse);
        return new FolderTree(
                folder.id(),
                folder.path(),
                folder.remoteUrl(),
                folder.branch(),
                head.map(RevCommit::name).orElse(null),
                project.map(ProjectSpec::project).orElse(null),
                project.map(spec -> List.copyOf(spec.targets().keySet())).orElse(List.of()),
                ahead,
                tracking == null ? null : tracking.getBehindCount(),
                List.copyOf(files.values()));
    }

    List<Problem> problems(GitFolder folder, List<Draft> drafts) {
        List<Problem> problems = new ArrayList<>();
        for (Draft draft : drafts) {
            checks.check(draft.path(), draft.content())
                    .forEach(problem ->
                            problems.add(new Problem(draft.path() + ": " + problem.message(), problem.line())));
        }
        if (drafts.stream().anyMatch(draft -> JavaChecks.applies(draft.path()))) {
            java.compile(transformations(folder))
                    .problems()
                    .forEach(located -> problems.add(new Problem(
                            (located.path() == null ? "" : located.path() + ": ")
                                    + located.problem().message(),
                            located.problem().line())));
        }
        return problems;
    }

    private void keep(GitFolder folder, String file, String content, Person person) {
        String head = cache.with(folder, clone -> clone.head().orElseThrow().name());
        store.saveDraft(new Draft(folder.id(), folder.branch(), file, content, head, person.id(), clock.instant()));
    }

    private List<Problem> problems(GitFolder folder, String path, String content) {
        if (content == null || !JavaChecks.applies(path)) {
            return checks.check(path, content);
        }
        Map<String, String> sources = transformations(folder);
        sources.put(path, content);
        return java.compile(sources).problemsOf(path);
    }

    private Map<String, String> transformations(GitFolder folder) {
        Map<String, String> sources = cache.with(folder, clone -> {
            Map<String, String> committed = new TreeMap<>();
            Optional<RevCommit> head = clone.head();
            if (head.isPresent()) {
                clone.files(head.get(), JavaChecks::applies)
                        .forEach((path, bytes) -> committed.put(path, new String(bytes, StandardCharsets.UTF_8)));
            }
            return committed;
        });
        for (Draft draft : store.drafts(folder.id(), folder.branch())) {
            if (!JavaChecks.applies(draft.path())) {
                continue;
            }
            if (draft.deleted()) {
                sources.remove(draft.path());
            } else {
                sources.put(draft.path(), draft.content());
            }
        }
        return sources;
    }

    private Map<String, Draft> drafts(GitFolder folder) {
        return store.drafts(folder.id(), folder.branch()).stream()
                .collect(Collectors.toMap(Draft::path, draft -> draft, (a, b) -> b, TreeMap::new));
    }
}
