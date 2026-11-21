package com.dataflow.workspace.folders;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

public final class Branches {

    static final String ORIGIN = "origin";
    static final String REMOTE = Constants.R_REMOTES + ORIGIN + "/";

    private Branches() {}

    public static String remote(String branch) {
        return REMOTE + branch;
    }

    static String check(String branch) {
        if (branch == null || !Repository.isValidRefName(Constants.R_HEADS + branch)) {
            throw new IllegalArgumentException("Not a valid branch name: '" + branch + "'");
        }
        return branch;
    }
}
