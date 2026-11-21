package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.RustFsPolicies;
import com.dataflow.workspace.config.WorkspaceProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

    private final Access access;
    private final RustFsPolicies policies;
    private final String signInUrl;

    public StorageController(Access access, RustFsPolicies policies, WorkspaceProperties properties) {
        this.access = access;
        this.policies = policies;
        this.signInUrl = properties.links().objectStore();
    }

    @GetMapping
    public View view() {
        return new View(policies.status(), signInUrl, policies.paths(access.caller()));
    }

    @PostMapping("/sync")
    public RustFsPolicies.Status sync() {
        access.requireAdmin();
        policies.apply();
        return policies.status();
    }

    public record View(RustFsPolicies.Status status, String signInUrl, List<RustFsPolicies.DatasetPaths> datasets) {}
}
