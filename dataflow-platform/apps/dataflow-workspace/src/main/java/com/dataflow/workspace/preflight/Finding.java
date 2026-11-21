package com.dataflow.workspace.preflight;

public record Finding(String subject, Verdict verdict, String detail) {

    public enum Verdict {
        PASSED,
        WARNING,
        FAILED
    }

    static Finding passed(String subject, String detail) {
        return new Finding(subject, Verdict.PASSED, detail);
    }

    static Finding warning(String subject, String detail) {
        return new Finding(subject, Verdict.WARNING, detail);
    }

    static Finding failed(String subject, String detail) {
        return new Finding(subject, Verdict.FAILED, detail);
    }
}
