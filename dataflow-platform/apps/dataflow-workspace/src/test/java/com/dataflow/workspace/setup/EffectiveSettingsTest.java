package com.dataflow.workspace.setup;

import com.dataflow.workspace.runs.EngineSettingsRun;
import com.dataflow.spec.settings.RecordedSetting;
import com.dataflow.workspace.runs.EventLog;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.runs.TowerException;
import com.dataflow.workspace.serving.RecordingClickHouse;
import com.dataflow.workspace.serving.ServingQueryException;
import com.dataflow.workspace.serving.SystemTables;
import com.dataflow.workspace.setup.EffectiveSettings.Kind;
import com.dataflow.workspace.setup.EffectiveSettings.Section;
import com.dataflow.workspace.setup.EffectiveSettings.Setting;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EffectiveSettingsTest {

    private static final String WORKSPACE = "dataflow.workspace.";
    private static final List<String> SETTING_COLUMNS = List.of("name", "value", "defaultValue", "description");
    private static final String ENGINE = "dataflow.engine.";

    private final EventLog events = mock(EventLog.class);
    private final Tower tower = mock(Tower.class);

    private static Setting setting(List<Section> sections, String key) {
        return sections.stream()
                .flatMap(section -> section.settings().stream())
                .filter(setting -> setting.name().equals(WORKSPACE + key))
                .findFirst()
                .orElseThrow();
    }

    private static Section section(List<Section> sections, String name) {
        return sections.stream().filter(section -> section.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void everySettingIsListedInItsSectionWithItsMeaning() {
        List<Section> sections = new EffectiveSettings(new MockEnvironment(), new ObjectMapper(),
                new SystemTables(new RecordingClickHouse()), events, tower).view().workspace();

        assertThat(sections)
                .extracting(Section::name)
                .contains(WORKSPACE + "auth", WORKSPACE + "git", WORKSPACE + "git.github")
                .isSorted();
        assertThat(sections).allMatch(section -> !section.description().isBlank());
        assertThat(sections)
                .flatMap(Section::settings)
                .allMatch(setting -> !setting.description().isBlank());
        assertThat(section(sections, WORKSPACE + "git.github").settings())
                .extracting(Setting::name)
                .allMatch(name -> name.startsWith(WORKSPACE + "git.github."));
    }

    @Test
    void valuesAreTypedAndSecretsAreMasked() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(WORKSPACE + "serving.url", "http://clickhouse:8123")
                .withProperty(WORKSPACE + "serving.password", "do-not-show")
                .withProperty(WORKSPACE + "alerts.token-file", "/var/run/dataflow/grafana/grafana.token")
                .withProperty(WORKSPACE + "auth.admins", "admins,platform")
                .withProperty(WORKSPACE + "dashboards[0].uid", "dataflow-runs")
                .withProperty(WORKSPACE + "dashboards[0].title", "Runs")
                .withProperty(WORKSPACE + "releases.path-style", "true")
                .withProperty(WORKSPACE + "alerts.grafana", "");
        List<Section> sections = new EffectiveSettings(environment, new ObjectMapper(),
                new SystemTables(new RecordingClickHouse()), events, tower).view().workspace();

        assertThat(setting(sections, "serving.url").value()).isEqualTo("http://clickhouse:8123");
        assertThat(setting(sections, "serving.password"))
                .returns(Kind.SECRET, Setting::kind)
                .returns(EffectiveSettings.MASK, Setting::value);
        assertThat(setting(sections, "alerts.token-file"))
                .returns(Kind.TEXT, Setting::kind)
                .returns("/var/run/dataflow/grafana/grafana.token", Setting::value);
        assertThat(setting(sections, "auth.admins"))
                .returns(Kind.LIST, Setting::kind)
                .returns(List.of("admins", "platform"), Setting::value);
        assertThat(setting(sections, "dashboards"))
                .returns(Kind.ENTRIES, Setting::kind)
                .returns(List.of(Map.of("uid", "dataflow-runs", "title", "Runs")), Setting::value);
        assertThat(setting(sections, "releases.path-style"))
                .returns(Kind.FLAG, Setting::kind)
                .returns("true", Setting::value);
        assertThat(setting(sections, "serving.timeout").kind()).isEqualTo(Kind.DURATION);
        assertThat(setting(sections, "serving.max-rows").kind()).isEqualTo(Kind.NUMBER);
        assertThat(setting(sections, "alerts.grafana").value()).isNull();
    }

    @Test
    void changedClickHouseSettingsKeepTheirDefaultAndFirstParagraph() {
        RecordingClickHouse clickHouse = new RecordingClickHouse().answer(
                statement -> statement.contains("server_settings"),
                SETTING_COLUMNS,
                List.of(List.of("max_thread_pool_size", "12000", "10000",
                        "Limits the threads\nof the global pool.\n\n**Example**")));

        List<Section> serving = new EffectiveSettings(new MockEnvironment(), new ObjectMapper(),
                new SystemTables(clickHouse), events, tower).view().serving();

        assertThat(serving).extracting(Section::name).containsExactly(EffectiveSettings.SERVER, EffectiveSettings.QUERIES);
        assertThat(section(serving, EffectiveSettings.SERVER).settings())
                .singleElement()
                .returns("12000", Setting::value)
                .returns("10000", Setting::defaultValue)
                .returns("Limits the threads of the global pool.", Setting::description);
        assertThat(section(serving, EffectiveSettings.QUERIES).settings()).isEmpty();
    }

    @Test
    void clickHouseMarkdownReadsAsPlainText() {
        assertThat(EffectiveSettings.summary(
                        "Same as [`concurrent_threads_soft_limit_num`](#concurrent_threads_soft_limit_num), but with `ratio`.\n\nMore."))
                .isEqualTo("Same as concurrent_threads_soft_limit_num, but with ratio.");
    }

    @Test
    void emptySecretsStayEmpty() {
        assertThat(EffectiveSettings.shown(WORKSPACE + "releases.secret-key", "")).isEmpty();
        assertThat(EffectiveSettings.shown(WORKSPACE + "releases.bucket", "releases")).isEqualTo("releases");
    }

    @Test
    void aClickHouseThatDoesNotAnswerIsSaidSo() {
        SystemTables down = mock(SystemTables.class);
        ServingQueryException refused =
                new ServingQueryException("Serving (ClickHouse) is not reachable at http://clickhouse:8123");
        when(down.changedServerSettings()).thenThrow(refused);
        when(down.changedQuerySettings()).thenThrow(refused);

        List<Section> serving = new EffectiveSettings(new MockEnvironment(), new ObjectMapper(), down, events, tower).view().serving();

        assertThat(serving).allSatisfy(section -> assertThat(section.description())
                .isEqualTo("ClickHouse did not answer: Serving (ClickHouse) is not reachable at http://clickhouse:8123"));
    }

    @Test
    void engineSettingsAreTheOnesTheLatestRunRecorded() {
        when(events.latestEngineSettings()).thenReturn(Optional.of(new EngineSettingsRun("earthquakes", "run-1",
                "2026-11-09T10:00:00Z", """
                [{"name":"dataflow.engine.maintenance.interval","type":"java.time.Duration","description":"Least time","value":"1h"},
                 {"name":"dataflow.engine.serving.enabled","type":"java.lang.Boolean","description":"Publish","value":"true"},
                 {"name":"dataflow.engine.serving.url","type":"java.lang.String","description":"Address","value":""}]""")));

        List<Section> engine = new EffectiveSettings(new MockEnvironment(), new ObjectMapper(),
                new SystemTables(new RecordingClickHouse()), events, tower).view().engine();

        assertThat(engine).extracting(Section::name).containsExactly(ENGINE + "maintenance", ENGINE + "serving");
        assertThat(section(engine, ENGINE + "maintenance").settings())
                .singleElement()
                .returns(Kind.DURATION, Setting::kind)
                .returns("1h", Setting::value)
                .returns("Least time", Setting::description)
                .returns("earthquakes run run-1 at 2026-11-09T10:00:00Z", Setting::origin);
        assertThat(section(engine, ENGINE + "serving").settings())
                .extracting(Setting::kind, Setting::value)
                .containsExactly(tuple(Kind.FLAG, "true"), tuple(Kind.TEXT, null));
    }

    @Test
    void anEngineWithoutARecordedRunIsSaidSo() {
        List<Section> engine = new EffectiveSettings(new MockEnvironment(), new ObjectMapper(),
                new SystemTables(new RecordingClickHouse()), events, tower).view().engine();

        assertThat(engine).singleElement()
                .returns(EffectiveSettings.NO_ENGINE_RUN, Section::description)
                .returns(List.of(), Section::settings);
    }

    @Test
    void towerSettingsAreGroupedByBlockWithTheirKind() {
        when(tower.settings()).thenReturn(List.of(
                new RecordedSetting("tower.target", "String", "Release target", "prod"),
                new RecordedSetting("tower.freshness_hours", "Int", "Stale after", "12"),
                new RecordedSetting("tower.docker.image", "String", "Engine image", "dataflow-engine:latest"),
                new RecordedSetting("tower.docker.data_dir", "String", "Cache folder", "")));

        List<Section> sections = new EffectiveSettings(new MockEnvironment(), new ObjectMapper(),
                new SystemTables(new RecordingClickHouse()), events, tower).view().tower();

        assertThat(sections).extracting(Section::name).containsExactly("tower", "tower.docker");
        assertThat(section(sections, "tower").settings())
                .extracting(Setting::name, Setting::kind, Setting::value)
                .containsExactly(tuple("tower.target", Kind.TEXT, "prod"), tuple("tower.freshness_hours", Kind.NUMBER, "12"));
        assertThat(section(sections, "tower.docker").settings())
                .extracting(Setting::value)
                .containsExactly("dataflow-engine:latest", null);
    }

    @Test
    void aTowerThatDoesNotAnswerIsSaidSo() {
        when(tower.settings()).thenThrow(new TowerException("The tower (Dagster) is not reachable at http://tower:3000/graphql"));

        List<Section> sections = new EffectiveSettings(new MockEnvironment(), new ObjectMapper(),
                new SystemTables(new RecordingClickHouse()), events, tower).view().tower();

        assertThat(sections).singleElement()
                .returns("The tower did not answer: The tower (Dagster) is not reachable at http://tower:3000/graphql",
                        Section::description)
                .returns(List.of(), Section::settings);
    }
}
