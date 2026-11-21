package com.dataflow.workspace.web;

import com.dataflow.spec.ComputePolicy;
import com.dataflow.spec.ComputeSpec;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.compute.ProjectCompute;
import com.dataflow.workspace.compute.ProjectCompute.ProjectView;
import com.dataflow.workspace.compute.ProjectCompute.Rights;
import com.dataflow.workspace.folders.FolderViews.FolderSummary;
import com.dataflow.workspace.folders.GitFolders;
import com.dataflow.workspace.folders.People;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@RestController
@RequestMapping("/api/compute")
public class ComputeController {

    private final ProjectCompute compute;
    private final GitFolders folders;
    private final Access access;
    private final People people;

    public ComputeController(ProjectCompute compute, GitFolders folders, Access access, People people) {
        this.compute = compute;
        this.folders = folders;
        this.access = access;
        this.people = people;
    }

    @GetMapping
    public List<ProjectView> projects() {
        return compute.projects(rights());
    }

    @GetMapping("/policy")
    public ComputePolicy policy() {
        return compute.policy();
    }

    @PutMapping("/{folderId}/{target}")
    public ProjectView update(@PathVariable UUID folderId, @PathVariable String target, @RequestBody ComputeSpec body) {
        access.require(Privilege.EDIT, Securable.folder(folders.folder(folderId).path()));
        return compute.update(folderId, target, body, people.current(), rights());
    }

    private Function<FolderSummary, Rights> rights() {
        Caller caller = access.caller();
        return folder -> {
            Securable securable = Securable.folder(folder.path());
            return new Rights(
                    access.can(caller, Privilege.READ, securable),
                    access.can(caller, Privilege.EDIT, securable),
                    access.can(caller, Privilege.MANAGE, securable));
        };
    }
}
