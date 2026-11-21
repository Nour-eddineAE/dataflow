package com.dataflow.engine.cli;

import com.dataflow.spec.settings.RecordedSetting;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
class EngineSettings {

    private static final String PREFIX = "dataflow.engine.";
    private static final String TASKS = PREFIX + "tasks.";
    private static final Pattern SECRET = Pattern.compile(".*(password|secret|key|token)$");
    private static final ObjectMapper JSON =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Environment environment;

    EngineSettings(Environment environment) {
        this.environment = environment;
    }

    List<RecordedSetting> recorded() {
        return metadata().properties().stream()
                .filter(property -> property.name().startsWith(PREFIX))
                .filter(property -> !property.name().startsWith(TASKS))
                .filter(property -> !SECRET.matcher(property.name()).matches())
                .map(property -> new RecordedSetting(
                        property.name(),
                        Objects.requireNonNullElse(property.type(), ""),
                        Objects.requireNonNullElse(property.description(), ""),
                        Objects.requireNonNullElse(environment.getProperty(property.name()), "")))
                .sorted(Comparator.comparing(RecordedSetting::name))
                .toList();
    }

    private static Metadata metadata() {
        try (InputStream in = new ClassPathResource(RecordedSetting.METADATA).getInputStream()) {
            return JSON.readValue(in, Metadata.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the engine's settings metadata " + RecordedSetting.METADATA, e);
        }
    }

    private record Metadata(List<Property> properties) {}

    private record Property(String name, String type, String description) {}
}
