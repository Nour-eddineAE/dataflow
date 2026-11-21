package com.dataflow.spec;

import java.util.List;

public class InvalidPipelineSpecException extends RuntimeException {

    private final List<String> problems;

    public InvalidPipelineSpecException(String source, List<String> problems) {
        super("Invalid pipeline spec " + source + ":\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public InvalidPipelineSpecException(String source, String problem, Throwable cause) {
        super("Invalid pipeline spec " + source + ": " + problem, cause);
        this.problems = List.of(problem);
    }

    public List<String> problems() {
        return problems;
    }
}
