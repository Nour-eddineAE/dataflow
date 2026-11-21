package com.dataflow.workspace.setup;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SettingOrigin {

    private static final String BINDER_SOURCE = "configurationProperties";
    private static final String COMMAND_LINE_SOURCE = "commandLineArgs";
    private static final String COMMAND_LINE = "Command line";
    private static final String ENVIRONMENT = "Environment ";
    private static final String NAME_SEPARATOR = ", ";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}$]+)");
    private static final Pattern RESOURCE = Pattern.compile("\\[([^]]+)]");

    private final ConfigurableEnvironment environment;

    SettingOrigin(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    String of(String name) {
        Set<String> variables = new LinkedHashSet<>();
        PropertySource<?> source = trace(name, variables, new HashSet<>());
        if (!variables.isEmpty()) {
            return ENVIRONMENT + String.join(NAME_SEPARATOR, variables);
        }
        return source == null ? null : label(source);
    }

    private PropertySource<?> trace(String name, Set<String> variables, Set<String> seen) {
        if (!seen.add(name)) {
            return null;
        }
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source.getName().equals(BINDER_SOURCE) || !source.containsProperty(name)) {
                continue;
            }
            if (source.getName().equals(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                variables.add(name.toUpperCase(Locale.ROOT).replaceAll("[.-]", "_"));
                return source;
            }
            Matcher placeholders = PLACEHOLDER.matcher(String.valueOf(source.getProperty(name)));
            while (placeholders.find()) {
                trace(placeholders.group(1), variables, seen);
            }
            return source;
        }
        return null;
    }

    private static String label(PropertySource<?> source) {
        if (source.getName().equals(COMMAND_LINE_SOURCE)) {
            return COMMAND_LINE;
        }
        Matcher resource = RESOURCE.matcher(source.getName());
        return resource.find() ? resource.group(1) : source.getName();
    }
}
