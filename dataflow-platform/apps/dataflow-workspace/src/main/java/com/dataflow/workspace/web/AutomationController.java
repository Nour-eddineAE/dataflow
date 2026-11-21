package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AuditAction;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.runs.Automation;
import com.dataflow.workspace.runs.Trigger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private final Automation automation;
    private final Access access;

    public AutomationController(Automation automation, Access access) {
        this.automation = automation;
        this.access = access;
    }

    @GetMapping
    public List<Trigger> triggers() {
        Caller caller = access.caller();
        return automation.triggers().stream()
                .filter(trigger -> trigger.dataset() == null ? caller.admin() : access.sees(caller, trigger.dataset()))
                .toList();
    }

    @PostMapping("/{name}/pause")
    public Trigger pause(@PathVariable String name) {
        Trigger trigger = automation.find(name);
        requireRun(trigger);
        Trigger paused = automation.pause(name);
        record(AuditAction.PAUSE, trigger);
        return paused;
    }

    @PostMapping("/{name}/resume")
    public Trigger resume(@PathVariable String name) {
        Trigger trigger = automation.find(name);
        requireRun(trigger);
        Trigger resumed = automation.resume(name);
        record(AuditAction.RESUME, trigger);
        return resumed;
    }

    private void record(AuditAction action, Trigger trigger) {
        if (trigger.dataset() != null) {
            access.record(action, Securable.dataset(trigger.dataset()));
        }
    }

    private void requireRun(Trigger trigger) {
        if (trigger.dataset() == null) {
            access.requireAdmin();
        } else {
            access.require(Privilege.RUN, Securable.dataset(trigger.dataset()));
        }
    }
}
