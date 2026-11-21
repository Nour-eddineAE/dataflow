package com.dataflow.workspace.access;

import java.time.Instant;

public record Grant(
        Securable securable, Principal principal, Privilege privilege, String grantedBy, Instant grantedAt) {}
