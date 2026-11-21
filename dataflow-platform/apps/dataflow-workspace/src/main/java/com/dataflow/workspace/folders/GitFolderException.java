package com.dataflow.workspace.folders;

import com.dataflow.workspace.specs.Problem;

import java.util.List;

public class GitFolderException extends RuntimeException {

    private final List<Problem> problems;

    public GitFolderException(String message) {
        this(message, List.of(), null);
    }

    public GitFolderException(String message, Throwable cause) {
        this(message, List.of(), cause);
    }

    public GitFolderException(String message, List<Problem> problems, Throwable cause) {
        super(message, cause);
        this.problems = List.copyOf(problems);
    }

    public List<Problem> problems() {
        return problems;
    }
}
