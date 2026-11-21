package com.dataflow.spec.compile;

import java.util.List;

public class CompilationFailedException extends RuntimeException {

    private final List<Diagnosis> diagnoses;

    public CompilationFailedException(List<Diagnosis> diagnoses) {
        super("Transformations do not compile:\n  - "
                + String.join(
                        "\n  - ", diagnoses.stream().map(Diagnosis::toString).toList()));
        this.diagnoses = List.copyOf(diagnoses);
    }

    public List<Diagnosis> diagnoses() {
        return diagnoses;
    }

    public record Diagnosis(String path, Long line, String message) {

        @Override
        public String toString() {
            return path == null ? message : path + ":" + line + ": " + message;
        }
    }
}
