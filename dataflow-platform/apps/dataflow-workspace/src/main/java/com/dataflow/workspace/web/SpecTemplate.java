package com.dataflow.workspace.web;

import com.dataflow.spec.Names;
import com.dataflow.workspace.templates.Templates;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SpecTemplate {

    private final Templates templates = Templates.load("templates", "yml");

    String render(String dataset) {
        if (dataset == null || !Names.DATASET.matcher(dataset).matches()) {
            throw new IllegalArgumentException(
                    "A dataset name is lower case letters, digits and _, starting with a letter");
        }
        return templates.render("pipeline", Map.of("dataset", dataset)) + "\n";
    }
}
