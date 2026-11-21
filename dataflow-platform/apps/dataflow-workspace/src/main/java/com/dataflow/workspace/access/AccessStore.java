package com.dataflow.workspace.access;

import java.util.List;

public interface AccessStore {

    List<Grant> grants();

    void add(Grant grant);

    boolean remove(Securable securable, Principal principal, Privilege privilege);

    void audit(AuditEntry entry);

    List<AuditEntry> audits(int limit);
}
