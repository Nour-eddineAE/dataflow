package com.dataflow.engine.cli;

import com.dataflow.spec.settings.RecordedSetting;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EngineSettingsTest {

    private static final String ENGINE = "dataflow.engine.";

    private static List<RecordedSetting> recorded(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return new EngineSettings(environment).recorded();
    }

    @Test
    void recordsEveryEngineSettingWithItsValueMeaningAndType() {
        List<RecordedSetting> settings = recorded(Map.of(
                ENGINE + "maintenance.interval", "2h",
                ENGINE + "serving.enabled", "true"));

        assertThat(settings).extracting(RecordedSetting::name).isSorted().allMatch(name -> name.startsWith(ENGINE));
        assertThat(settings).allMatch(setting -> !setting.description().isBlank());
        assertThat(settings)
                .filteredOn(setting -> setting.name().equals(ENGINE + "maintenance.interval"))
                .singleElement()
                .returns("2h", RecordedSetting::value)
                .returns("java.time.Duration", RecordedSetting::type);
        assertThat(settings)
                .filteredOn(setting -> setting.name().equals(ENGINE + "serving.url"))
                .singleElement()
                .returns("", RecordedSetting::value);
    }

    @Test
    void leavesOutSecretsAndTaskInputs() {
        List<RecordedSetting> settings = recorded(Map.of(
                ENGINE + "serving.password", "do-not-record",
                ENGINE + "tasks.draft", "ZHJhZnQ="));

        assertThat(settings)
                .extracting(RecordedSetting::name)
                .doesNotContain(ENGINE + "serving.password", ENGINE + "tasks.draft", ENGINE + "tasks.sample-rows");
        assertThat(settings).extracting(RecordedSetting::value).doesNotContain("do-not-record");
    }

    @Test
    void serializesAsObjectsInTheRunEvent() throws JsonProcessingException {
        String json = new ObjectMapper().writeValueAsString(
                List.of(new RecordedSetting(ENGINE + "cache-dir", "java.lang.String", "Directory", "/data")));

        assertThat(json).isEqualTo(
                "[{\"name\":\"dataflow.engine.cache-dir\",\"type\":\"java.lang.String\",\"description\":\"Directory\",\"value\":\"/data\"}]");
    }
}
