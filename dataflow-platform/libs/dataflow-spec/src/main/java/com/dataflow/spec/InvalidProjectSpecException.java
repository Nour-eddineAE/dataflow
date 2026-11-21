package com.dataflow.spec;

import java.util.List;

public class InvalidProjectSpecException extends RuntimeException {

    private final List<String> problems;

    public InvalidProjectSpecException(String source, List<String> problems) {
        super("Invalid project " + source + ":\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public InvalidProjectSpecException(String source, String problem, Throwable cause) {
        super("Invalid project " + source + ": " + problem, cause);
        this.problems = List.of(problem);
    }

    public List<String> problems() {
        return problems;
    }
}
