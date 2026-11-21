package com.dataflow.workspace.web;

import com.dataflow.spec.Names;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.DraftAccess;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.folders.GitFolders;
import com.dataflow.workspace.specs.SpecCheck;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.tasks.EngineTasks;
import com.dataflow.workspace.tasks.TaskStates;
import com.dataflow.workspace.tasks.TaskKind;
import com.dataflow.workspace.tasks.TaskStarted;
import com.dataflow.workspace.tasks.TaskState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final EngineTasks tasks;
    private final TaskStates states;
    private final Access access;
    private final GitFolders folders;
    private final SpecStore specs;
    private final DraftAccess drafts;

    public TaskController(
            EngineTasks tasks, TaskStates states, Access access, GitFolders folders, SpecStore specs, DraftAccess drafts) {
        this.tasks = tasks;
        this.states = states;
        this.access = access;
        this.folders = folders;
        this.specs = specs;
        this.drafts = drafts;
    }

    private static String checkedDataset(String dataset) {
        if (dataset == null || !Names.DATASET.matcher(dataset).matches()) {
            throw new IllegalArgumentException("A dataset name is a-z, 0-9 and _, starting with a letter");
        }
        return dataset;
    }

    @PostMapping
    public TaskStarted start(@RequestBody Request request) {
        TaskKind kind = TaskKind.of(request.kind());
        String dataset = checkedDataset(request.dataset());
        if (request.folderId() != null) {
            access.require(
                    Privilege.RUN,
                    Securable.folder(folders.folder(request.folderId()).path()));
        }
        if (kind != TaskKind.INFER && specs.find(dataset).isPresent()) {
            access.require(Privilege.RUN, Securable.dataset(dataset));
        }
        if (kind == TaskKind.INFER) {
            drafts.requireSource(request.source());
            return tasks.infer(dataset, request.source());
        }
        SpecCheck check = specs.check(request.content() == null ? "" : request.content(), dataset);
        if (check.valid()) {
            drafts.require(check.spec());
        }
        return tasks.start(kind, dataset, request.content(), request.folderId());
    }

    @GetMapping("/{taskId}")
    public TaskState state(@PathVariable String taskId, @RequestParam String run) {
        Caller caller = access.caller();
        return states.state(taskId, run, caller.admin() ? null : caller.id());
    }

    public record Request(String kind, String dataset, String content, java.util.UUID folderId, String source) {}
}
