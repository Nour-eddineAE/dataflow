package com.dataflow.workspace.folders;

import com.dataflow.spec.ProjectSpec;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.folders.FolderViews.CreateFolder;
import com.dataflow.workspace.folders.FolderViews.FolderSummary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class GitFolders {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String SHARED = "Shared/";

    private final FolderStore store;
    private final GitCache cache;
    private final FolderProjects projects;
    private final List<Path> fileRemotes;
    private final Clock clock;

    public GitFolders(
            FolderStore store, GitCache cache, FolderProjects projects, WorkspaceProperties properties, Clock clock) {
        this.store = store;
        this.cache = cache;
        this.projects = projects;
        this.fileRemotes = properties.git().fileRemotes().stream()
                .filter(remote -> !remote.isBlank())
                .map(remote -> Path.of(remote).toAbsolutePath().normalize())
                .toList();
        this.clock = clock;
    }

    public List<FolderSummary> list() {
        return store.folders().stream().map(this::summary).toList();
    }

    public GitFolder create(CreateFolder request, Person person) {
        if (request.name() == null || !NAME.matcher(request.name()).matches()) {
            throw new IllegalArgumentException("A folder name is 1 to 64 letters, digits, '.', '_' or '-'");
        }
        checkRemote(request.remoteUrl());
        String branch = Branches.check(
                request.branch() == null || request.branch().isBlank() ? "main" : request.branch().strip());
        if (request.credentialId() != null
                && store.credentials(person.id()).stream().noneMatch(owned -> owned.id().equals(request.credentialId()))) {
            throw new IllegalArgumentException("No Git credential " + request.credentialId());
        }
        String path = (request.shared() ? SHARED : Person.USERS_FOLDER + person.id() + "/") + request.name();
        if (store.folders().stream().anyMatch(folder -> folder.path().equals(path))) {
            throw new IllegalArgumentException("A folder " + path + " already exists");
        }
        GitFolder folder = new GitFolder(
                UUID.randomUUID(),
                path,
                request.remoteUrl().strip(),
                branch,
                request.credentialId(),
                person.id(),
                clock.instant());
        store.insertFolder(folder);
        try {
            boolean empty = cache.with(folder, clone -> clone.head().isEmpty());
            if (empty) {
                store.saveDraft(new Draft(
                        folder.id(),
                        branch,
                        ProjectSpec.FILE,
                        FolderProjects.starter(request.name()),
                        null,
                        person.id(),
                        clock.instant()));
            }
        } catch (RuntimeException e) {
            store.deleteFolder(folder.id());
            cache.forget(folder.id());
            throw e;
        }
        return folder;
    }

    public void delete(UUID id) {
        folder(id);
        store.deleteFolder(id);
        cache.forget(id);
    }

    public GitFolder folder(UUID id) {
        return store.folder(id).orElseThrow(() -> new UnknownFolderException(id));
    }

    private FolderSummary summary(GitFolder folder) {
        int drafts = store.drafts(folder.id(), folder.branch()).size();
        String project;
        try {
            project = cache.with(folder, clone -> clone.file(ProjectSpec.FILE)
                    .flatMap(projects::parse)
                    .map(ProjectSpec::project)
                    .orElse(null));
        } catch (RuntimeException e) {
            project = null;
        }
        return new FolderSummary(
                folder.id(), folder.path(), folder.remoteUrl(), folder.branch(), folder.owner(), project, drafts);
    }

    private void checkRemote(String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a Git remote URL: '" + url + "'");
        }
        if ("https".equals(uri.getScheme()) && uri.getHost() != null) {
            return;
        }
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
            Path local = Path.of(uri.getPath()).toAbsolutePath().normalize();
            if (fileRemotes.stream().anyMatch(local::startsWith)) {
                return;
            }
            throw new IllegalArgumentException("Local remotes are allowed only under " + fileRemotes);
        }
        throw new IllegalArgumentException("A Git remote must be an https:// URL, got '" + url + "'");
    }
}
