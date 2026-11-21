package com.dataflow.workspace.access;

import java.util.List;

public record Caller(String id, List<String> groups, boolean admin) {

    public boolean is(Principal principal) {
        return principal.kind() == Principal.Kind.USER
                ? principal.name().equals(id)
                : groups.contains(principal.name());
    }
}
