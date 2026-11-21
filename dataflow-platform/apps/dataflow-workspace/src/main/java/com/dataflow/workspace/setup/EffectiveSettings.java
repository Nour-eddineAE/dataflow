package com.dataflow.workspace.setup;

import com.dataflow.spec.settings.RecordedSetting;
import com.dataflow.workspace.runs.EngineSettingsRun;
import com.dataflow.workspace.runs.EventLog;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.serving.ServerSetting;
import com.dataflow.workspace.serving.SystemTables;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Component
public class EffectiveSettings {

    private static final String NAME = "name";

    static final String MASK = "••••••";
    static final String SERVER = "clickhouse.server";
    static final String QUERIES = "clickhouse.queries";
    private static final String SERVER_DESCRIPTION = "Server settings changed from their defaults in the ClickHouse configuration";
    private static final String QUERIES_DESCRIPTION = "Query settings changed for the Workspace's ClickHouse user";
    private static final String UNAVAILABLE = "ClickHouse did not answer: ";
    static final String ENGINE = "dataflow.engine";
    static final String NO_ENGINE_RUN = "No run has recorded the engine's settings yet";
    private static final String ENGINE_SOURCE = "%s run %s at %s";
    private static final String TOWER_UNAVAILABLE = "The tower did not answer: ";
    private static final String TOWER_SOURCE = "The tower's defs.yaml, from its environment variables";
    private static final String PREFIX = "dataflow.";
    private static final String LIST = "java.util.List<";
    private static final String TEXT_LIST = "java.util.List<java.lang.String>";
    private static final String PARAGRAPH = "\\R\\s*\\R";
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern SECRET = Pattern.compile(".*(password|secret|key|token)$");
    private static final TypeReference<List<RecordedSetting>> RECORDED = new TypeReference<>() {};
    private static final Bindable<List<String>> TEXTS = Bindable.listOf(String.class);
    private static final Bindable<List<Map<String, String>>> ENTRIES = Bindable.of(ResolvableType.forClassWithGenerics(
            List.class, ResolvableType.forClassWithGenerics(Map.class, String.class, String.class)));

    private final ConfigurableEnvironment environment;
    private final ObjectMapper json;
    private final SystemTables system;
    private final SettingOrigin origin;
    private final EventLog events;
    private final Tower tower;

    public EffectiveSettings(ConfigurableEnvironment environment, ObjectMapper json, SystemTables system, EventLog events,
                             Tower tower) {
        this.environment = environment;
        this.origin = new SettingOrigin(environment);
        this.json = json;
        this.system = system;
        this.events = events;
        this.tower = tower;
    }

    static String shown(String name, String value) {
        if (value == null) {
            return "";
        }
        return SECRET.matcher(name).matches() && !value.isBlank() ? MASK : value;
    }

    static Kind kind(String name, String type) {
        if (SECRET.matcher(name).matches()) {
            return Kind.SECRET;
        }
        return switch (type) {
            case "java.lang.Boolean", "boolean", "Bool" -> Kind.FLAG;
            case "java.lang.Integer", "java.lang.Long", "int", "long", "Int" -> Kind.NUMBER;
            case "java.time.Duration" -> Kind.DURATION;
            case TEXT_LIST -> Kind.LIST;
            default -> type.startsWith(LIST) ? Kind.ENTRIES : Kind.TEXT;
        };
    }

    static String summary(String description) {
        if (description == null) {
            return "";
        }
        String paragraph = description.strip().split(PARAGRAPH, 2)[0];
        return LINK.matcher(paragraph).replaceAll("$1").replace("`", "").replaceAll("\\s+", " ");
    }

    public View view() {
        return new View(workspace(), List.of(
                serving(SERVER, SERVER_DESCRIPTION, system::changedServerSettings),
                serving(QUERIES, QUERIES_DESCRIPTION, system::changedQuerySettings)),
                engine(), tower());
    }

    List<Section> tower() {
        List<RecordedSetting> settings;
        try {
            settings = tower.settings();
        } catch (RuntimeException e) {
            return List.of(new Section(PlatformServices.TOWER, TOWER_UNAVAILABLE + summary(e.getMessage()), List.of()));
        }
        return grouped(settings, TOWER_SOURCE);
    }

    List<Section> engine() {
        Optional<EngineSettingsRun> run;
        try {
            run = events.latestEngineSettings();
        } catch (RuntimeException e) {
            return List.of(new Section(ENGINE, UNAVAILABLE + summary(e.getMessage()), List.of()));
        }
        return run.map(this::engine).orElseGet(() -> List.of(new Section(ENGINE, NO_ENGINE_RUN, List.of())));
    }

    private List<Section> engine(EngineSettingsRun run) {
        String source = ENGINE_SOURCE.formatted(run.dataset(), run.runId(), run.at());
        return grouped(recorded(run), source);
    }

    private static List<Section> grouped(List<RecordedSetting> settings, String source) {
        Map<String, List<Setting>> groups = new TreeMap<>();
        for (RecordedSetting recorded : settings) {
            String name = recorded.name();
            groups.computeIfAbsent(name.substring(0, name.lastIndexOf('.')), group -> new ArrayList<>())
                    .add(new Setting(name, recorded.description(), kind(name, recorded.type()),
                            blankToNull(shown(name, recorded.value())), null, source));
        }
        return groups.entrySet().stream()
                .map(group -> new Section(group.getKey(), source, group.getValue()))
                .toList();
    }

    private List<RecordedSetting> recorded(EngineSettingsRun run) {
        try {
            return json.readValue(run.settings(), RECORDED);
        } catch (JacksonException e) {
            throw new IllegalStateException("Run " + run.runId() + " of " + run.dataset()
                    + " recorded engine settings that are not readable: " + e.getMessage(), e);
        }
    }

    List<Section> workspace() {
        JsonNode metadata = metadata();
        Binder binder = Binder.get(environment);
        Map<String, List<Setting>> groups = new TreeMap<>();
        for (JsonNode property : metadata.path("properties")) {
            String name = property.path(NAME).asString();
            if (name.startsWith(PREFIX)) {
                groups.computeIfAbsent(name.substring(0, name.lastIndexOf('.')), group -> new ArrayList<>())
                        .add(setting(binder, property));
            }
        }
        Map<String, String> descriptions = new HashMap<>();
        metadata.path("groups").forEach(group ->
                descriptions.put(group.path(NAME).asString(), group.path("description").asString("")));
        return groups.entrySet().stream()
                .map(group -> new Section(group.getKey(), descriptions.getOrDefault(group.getKey(), ""), group.getValue()))
                .toList();
    }

    private Setting setting(Binder binder, JsonNode property) {
        String name = property.path(NAME).asString();
        Kind kind = kind(name, property.path("type").asString());
        Object value = switch (kind) {
            case LIST -> binder.bind(name, TEXTS).orElse(List.of());
            case ENTRIES -> binder.bind(name, ENTRIES).orElse(List.of());
            default -> blankToNull(shown(name, environment.getProperty(name)));
        };
        return new Setting(name, property.path("description").asString(""), kind, value, null, origin.of(name));
    }

    private static Section serving(String name, String description, Supplier<List<ServerSetting>> changed) {
        List<ServerSetting> settings;
        try {
            settings = changed.get();
        } catch (RuntimeException e) {
            return new Section(name, UNAVAILABLE + summary(e.getMessage()), List.of());
        }
        return new Section(name, description, settings.stream()
                .map(setting -> new Setting(setting.name(), summary(setting.description()), Kind.TEXT,
                        setting.value(), setting.defaultValue(), null))
                .toList());
    }

    private static String blankToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private JsonNode metadata() {
        try (InputStream in = new ClassPathResource(RecordedSetting.METADATA).getInputStream()) {
            return json.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public enum Kind {
        TEXT,
        SECRET,
        FLAG,
        NUMBER,
        DURATION,
        LIST,
        ENTRIES
    }

    public record Setting(String name, String description, Kind kind, Object value, String defaultValue, String origin) {}

    public record Section(String name, String description, List<Setting> settings) {}

    public record View(List<Section> workspace, List<Section> serving, List<Section> engine, List<Section> tower) {}
}
