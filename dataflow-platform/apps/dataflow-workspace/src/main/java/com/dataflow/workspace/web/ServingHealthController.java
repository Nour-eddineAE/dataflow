package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.serving.ServingHealth;
import com.dataflow.workspace.serving.SystemTables;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServingHealthController {

    private final Access access;
    private final SystemTables system;

    public ServingHealthController(Access access, SystemTables system) {
        this.access = access;
        this.system = system;
    }

    @GetMapping("/api/services/serving-health")
    public ServingHealth health() {
        access.requireAdmin();
        return system.health();
    }
}
