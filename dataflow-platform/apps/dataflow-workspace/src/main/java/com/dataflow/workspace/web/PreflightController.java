package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.folders.GitFolders;
import com.dataflow.workspace.preflight.Finding;
import com.dataflow.workspace.preflight.Preflight;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/specs")
public class PreflightController {

    private final Preflight preflight;
    private final GitFolders folders;
    private final Access access;

    public PreflightController(Preflight preflight, GitFolders folders, Access access) {
        this.preflight = preflight;
        this.folders = folders;
        this.access = access;
    }

    @PostMapping("/preflight")
    public List<Finding> preflight(@RequestBody Request request) {
        if (request.folderId() != null) {
            access.require(Privilege.READ, Securable.folder(folders.folder(request.folderId()).path()));
        }
        return preflight.run(request.dataset(), request.content(), request.folderId());
    }

    public record Request(String dataset, String content, UUID folderId) {}
}
