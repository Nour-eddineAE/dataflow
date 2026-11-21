package com.dataflow.workspace.templates;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Templates {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z][a-z0-9_]*)}}");

    private final String folder;
    private final Map<String, String> texts;

    private Templates(String folder, Map<String, String> texts) {
        this.folder = folder;
        this.texts = texts;
    }

    public static Templates load(String folder, String extension) {
        String suffix = "." + extension;
        Map<String, String> texts = new TreeMap<>();
        try {
            for (Resource resource :
                    new PathMatchingResourcePatternResolver().getResources("classpath*:" + folder + "/*" + suffix)) {
                String file = resource.getFilename();
                if (file == null) {
                    continue;
                }
                String text = resource.getContentAsString(StandardCharsets.UTF_8);
                texts.put(file.substring(0, file.length() - suffix.length()), text.stripTrailing());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the " + folder + " templates", e);
        }
        if (texts.isEmpty()) {
            throw new IllegalStateException("No " + suffix + " templates under classpath:" + folder + "/");
        }
        return new Templates(folder, Map.copyOf(texts));
    }

    public Set<String> names() {
        return new TreeSet<>(texts.keySet());
    }

    public Set<String> placeholders(String name) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text(name));
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    public String render(String name) {
        return render(name, Map.of());
    }

    public String render(String name, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(text(name));
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException(
                        folder + "/" + name + " needs a value for {{" + matcher.group(1) + "}}");
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String text(String name) {
        String text = texts.get(name);
        if (text == null) {
            throw new IllegalArgumentException("No template " + folder + "/" + name + "; known: " + texts.keySet());
        }
        return text;
    }
}
