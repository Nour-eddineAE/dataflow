package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AuditAction;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.folders.GitFolders;
import com.dataflow.workspace.folders.People;
import com.dataflow.workspace.releases.ReleaseLog;
import com.dataflow.workspace.releases.ReleaseRecord;
import com.dataflow.workspace.releases.Releases;
import com.dataflow.workspace.releases.Releases.ReleaseView;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/releases")
public class ReleaseController {

    private static final int HISTORY = 50;

    private final Releases releases;
    private final ReleaseLog releaseLog;
    private final People people;
    private final Access access;
    private final GitFolders folders;

    public ReleaseController(Releases releases, ReleaseLog releaseLog, People people, Access access, GitFolders folders) {
        this.releases = releases;
        this.releaseLog = releaseLog;
        this.people = people;
        this.access = access;
        this.folders = folders;
    }

    @GetMapping
    public List<ReleaseView> current() {
        Caller caller = access.caller();
        return releases.current().stream()
                .map(view -> visible(caller, view))
                .flatMap(Optional::stream)
                .toList();
    }

    @GetMapping("/{project}")
    public List<ReleaseRecord> history(@PathVariable String project) {
        Caller caller = access.caller();
        boolean visible = caller.admin()
                || releases.current().stream()
                        .filter(view -> view.project().equals(project))
                        .anyMatch(view -> visible(caller, view).isPresent());
        return visible ? releaseLog.history(project, HISTORY) : List.of();
    }

    private Optional<ReleaseView> visible(Caller caller, ReleaseView view) {
        if (caller.admin() || caller.id().equals(view.releasedBy())) {
            return Optional.of(view);
        }
        List<String> seen = view.datasets().stream().filter(dataset -> access.sees(caller, dataset)).toList();
        return seen.isEmpty()
                ? Optional.empty()
                : Optional.of(new ReleaseView(
                        view.project(), view.target(), view.commit(), view.releasedAt(), view.releasedBy(), seen));
    }

    @PostMapping
    public ReleaseView release(@RequestBody Release body) {
        Securable folder = Securable.folder(folders.folder(body.folderId()).path());
        access.require(Privilege.MANAGE, folder);
        ReleaseView released = releases.release(body.folderId(), body.target(), people.current());
        access.record(AuditAction.RELEASE, folder);
        return released;
    }

    public record Release(UUID folderId, String target) {}
}
