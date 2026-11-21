package com.dataflow.workspace.access;

import java.util.ArrayList;
import java.util.List;

public class InMemoryAccessStore implements AccessStore {

    private final List<Grant> grants = new ArrayList<>();
    private final List<AuditEntry> audits = new ArrayList<>();

    @Override
    public List<Grant> grants() {
        return List.copyOf(grants);
    }

    @Override
    public void add(Grant grant) {
        boolean known = grants.stream()
                .anyMatch(existing -> existing.securable().equals(grant.securable())
                        && existing.principal().equals(grant.principal())
                        && existing.privilege() == grant.privilege());
        if (!known) {
            grants.add(grant);
        }
    }

    @Override
    public boolean remove(Securable securable, Principal principal, Privilege privilege) {
        return grants.removeIf(grant -> grant.securable().equals(securable)
                && grant.principal().equals(principal)
                && grant.privilege() == privilege);
    }

    @Override
    public void audit(AuditEntry entry) {
        audits.add(0, entry);
    }

    @Override
    public List<AuditEntry> audits(int limit) {
        return audits.subList(0, Math.min(limit, audits.size()));
    }
}
