package com.dataflow.spec;

import java.util.List;

public record BoardSpec(List<String> hide, List<String> first) {

    public static final String FOLDER = "dashboards";
    public static final BoardSpec NONE = new BoardSpec(List.of(), List.of());

    public BoardSpec {
        hide = hide == null ? List.of() : List.copyOf(hide);
        first = first == null ? List.of() : List.copyOf(first);
    }

    public static String file(String dataset) {
        return FOLDER + "/" + dataset + ".yml";
    }
}
