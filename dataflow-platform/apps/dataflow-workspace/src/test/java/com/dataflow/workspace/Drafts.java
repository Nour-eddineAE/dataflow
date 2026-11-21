package com.dataflow.workspace;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class Drafts {

    private static final PipelineSpecLoader LOADER = new PipelineSpecLoader();

    private Drafts() {}

    public static String text(String name) {
        try (InputStream in =
                Objects.requireNonNull(Drafts.class.getResourceAsStream("/pipelines/" + name + "/pipeline.yml"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PipelineSpec spec(String name) {
        return LOADER.parse(text(name), name);
    }

    public static PipelineSpec edited(String name, String from, String to) {
        String text = text(name);
        if (!text.contains(from)) {
            throw new IllegalArgumentException(name + " has no '" + from + "'");
        }
        return LOADER.parse(text.replace(from, to), name);
    }
}
