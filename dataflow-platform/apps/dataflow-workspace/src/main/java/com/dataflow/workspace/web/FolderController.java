package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Principal;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.folders.FolderFiles;
import com.dataflow.workspace.folders.FolderSync;
import com.dataflow.workspace.folders.FolderViews.CommitView;
import com.dataflow.workspace.folders.FolderViews.CreateFolder;
import com.dataflow.workspace.folders.FolderViews.FileContent;
import com.dataflow.workspace.folders.FolderViews.FolderSummary;
import com.dataflow.workspace.folders.FolderViews.FolderTree;
import com.dataflow.workspace.folders.GitFolder;
import com.dataflow.workspace.folders.GitFolders;
import com.dataflow.workspace.folders.JavaApi;
import com.dataflow.workspace.folders.JavaChecks;
import com.dataflow.workspace.folders.TransformationInfo;
import com.dataflow.workspace.folders.People;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FolderController {

    private static final Duration JAVA_API_AGE = Duration.ofHours(1);

    private final GitFolders folders;
    private final FolderFiles files;
    private final FolderSync sync;
    private final People people;
    private final Access access;
    private final JavaApi java;
    private final JavaChecks checks;

    public FolderController(
            GitFolders folders,
            FolderFiles files,
            FolderSync sync,
            People people,
            Access access,
            JavaApi java,
            JavaChecks checks) {
        this.access = access;
        this.java = java;
        this.checks = checks;
        this.folders = folders;
        this.files = files;
        this.sync = sync;
        this.people = people;
    }

    private void requireMember(String team, boolean shared) {
        if (!shared) {
            throw new IllegalArgumentException("A personal folder has no team; make it shared");
        }
        Caller caller = access.caller();
        if (!caller.admin() && !caller.groups().contains(team)) {
            throw new AccessDeniedException(caller.id() + " is not in the team " + team);
        }
    }

    @GetMapping("/editor/java")
    public ResponseEntity<List<JavaApi.JavaType>> javaApi() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(JAVA_API_AGE).cachePrivate())
                .body(java.types());
    }

    @GetMapping("/folders")
    public List<FolderSummary> folders() {
        Caller caller = access.caller();
        return folders.list().stream()
                .filter(folder -> access.can(caller, Privilege.READ, Securable.folder(folder.path())))
                .toList();
    }

    @PostMapping("/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public GitFolder create(@RequestBody CreateFolder request) {
        String team = request.team() == null || request.team().isBlank() ? null : request.team().strip();
        if (team != null) {
            requireMember(team, request.shared());
        }
        GitFolder created = folders.create(request, people.current());
        if (request.shared()) {
            Securable folder = Securable.folder(created.path());
            access.owner(folder, people.current().id());
            if (team != null) {
                access.ensure(folder, Principal.group(team), Privilege.MANAGE, people.current().id());
            }
        }
        return created;
    }

    @DeleteMapping("/folders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        require(Privilege.MANAGE, id);
        folders.delete(id);
    }

    @GetMapping("/folders/{id}/tree")
    public FolderTree tree(@PathVariable UUID id) {
        require(Privilege.READ, id);
        return files.tree(id);
    }

    @GetMapping("/folders/{id}/file")
    public FileContent read(@PathVariable UUID id, @RequestParam String path) {
        require(Privilege.READ, id);
        return files.read(id, path);
    }

    @GetMapping("/folders/{id}/transformations")
    public List<TransformationInfo> transformations(@PathVariable UUID id) {
        require(Privilege.READ, id);
        return checks.describe(files.transformations(id));
    }

    @PutMapping("/folders/{id}/file")
    public FileContent save(@PathVariable UUID id, @RequestParam String path, @RequestBody FileBody body) {
        require(Privilege.EDIT, id);
        return files.save(id, path, body.content(), people.current());
    }

    @DeleteMapping("/folders/{id}/file")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id, @RequestParam String path) {
        require(Privilege.EDIT, id);
        files.remove(id, path, people.current());
    }

    @PostMapping("/folders/{id}/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable UUID id, @RequestParam String path) {
        require(Privilege.EDIT, id);
        files.discard(id, path);
    }

    @GetMapping("/folders/{id}/branches")
    public List<String> branches(@PathVariable UUID id) {
        require(Privilege.READ, id);
        return sync.branches(id);
    }

    @PostMapping("/folders/{id}/checkout")
    public FolderTree checkout(@PathVariable UUID id, @RequestBody Checkout body) {
        require(Privilege.EDIT, id);
        return sync.checkout(id, body.branch());
    }

    @PostMapping("/folders/{id}/pull")
    public FolderTree pull(@PathVariable UUID id) {
        require(Privilege.EDIT, id);
        return sync.pull(id);
    }

    @PostMapping("/folders/{id}/commit")
    public CommitView commit(@PathVariable UUID id, @RequestBody CommitBody body) {
        require(Privilege.EDIT, id);
        return sync.commit(id, body.message(), people.current());
    }

    @PostMapping("/folders/{id}/push")
    public FolderTree push(@PathVariable UUID id) {
        require(Privilege.EDIT, id);
        return sync.push(id);
    }

    @GetMapping("/folders/{id}/log")
    public List<CommitView> log(@PathVariable UUID id) {
        require(Privilege.READ, id);
        return sync.log(id);
    }

    private void require(Privilege privilege, UUID id) {
        access.require(privilege, Securable.folder(folders.folder(id).path()));
    }

    public record FileBody(String content) {}

    public record Checkout(String branch) {}

    public record CommitBody(String message) {}
}
