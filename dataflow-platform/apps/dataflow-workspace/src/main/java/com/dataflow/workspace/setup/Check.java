package com.dataflow.workspace.setup;

public record Check(String name, boolean ok, String detail) {

    private static final int MAX_DETAIL = 240;

    static Check passed(String name, String detail) {
        return new Check(name, true, detail);
    }

    static Check failed(String name, String detail) {
        return new Check(name, false, detail);
    }

    static Check unreachable(String name, String url, String consequence) {
        return failed(name, "No answer at " + url + consequence);
    }

    static String firstLine(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        int end = message.indexOf('\n');
        String line = end < 0 ? message : message.substring(0, end);
        return line.length() > MAX_DETAIL ? line.substring(0, MAX_DETAIL) + "…" : line;
    }
}
