package com.dataflow.workspace.specs;

import java.util.List;

public class InvalidDraftException extends RuntimeException {

    private final List<Problem> problems;

    public InvalidDraftException(String dataset, List<Problem> problems) {
        super("The spec of " + dataset + " has " + problems.size() + " problem(s)");
        this.problems = List.copyOf(problems);
    }

    public List<Problem> problems() {
        return problems;
    }
}
