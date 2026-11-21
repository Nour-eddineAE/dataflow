package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AuditEntry;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Grant;
import com.dataflow.workspace.access.Principal;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/access")
public class AccessController {

    private static final int MAX_AUDITS = 500;

    private final Access access;

    public AccessController(Access access) {
        this.access = access;
    }

    private static Privilege privilege(String name) {
        try {
            return Privilege.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unknown privilege " + name + "; use READ, SELECT, RUN, EDIT or MANAGE");
        }
    }

    @GetMapping("/grants")
    public List<GrantView> grants() {
        Caller caller = access.caller();
        return access.grants().stream()
                .filter(grant ->
                        access.can(caller, Privilege.MANAGE, grant.securable()) || caller.is(grant.principal()))
                .map(GrantView::of)
                .toList();
    }

    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantView grant(@RequestBody Change change) {
        return GrantView.of(access.grant(
                Securable.parse(change.securable()),
                Principal.parse(change.principal()),
                privilege(change.privilege())));
    }

    @DeleteMapping("/grants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestParam String securable, @RequestParam String principal, @RequestParam String privilege) {
        access.revoke(Securable.parse(securable), Principal.parse(principal), privilege(privilege));
    }

    @GetMapping("/audit")
    public List<AuditEntry> audit(@RequestParam(defaultValue = "50") int limit) {
        access.requireAdmin();
        return access.audits(Limits.clamp(limit, MAX_AUDITS));
    }

    public record Change(String securable, String principal, String privilege) {}

    public record GrantView(String securable, String principal, String privilege, String grantedBy, String grantedAt) {

        static GrantView of(Grant grant) {
            return new GrantView(
                    grant.securable().toString(),
                    grant.principal().toString(),
                    grant.privilege().name(),
                    grant.grantedBy(),
                    String.valueOf(grant.grantedAt()));
        }
    }
}
