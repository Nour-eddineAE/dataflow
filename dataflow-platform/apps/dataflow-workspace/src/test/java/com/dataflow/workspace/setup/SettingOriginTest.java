package com.dataflow.workspace.setup;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettingOriginTest {

    private static final String YAML = "Config resource 'class path resource [application-docker.yml]' via location 'optional:classpath:/'";

    private final MockEnvironment environment = new MockEnvironment();
    private final SettingOrigin origin = new SettingOrigin(environment);

    private void files(Map<String, Object> properties) {
        environment.getPropertySources().addLast(new MapPropertySource(YAML, properties));
    }

    private void variables(Map<String, Object> variables) {
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
    }

    @Test
    void aValueWrittenInAFileNamesTheFile() {
        files(Map.of("dataflow.limit", "5"));

        assertThat(origin.of("dataflow.limit")).isEqualTo("application-docker.yml");
    }

    @Test
    void aPlaceholderLeadsToTheVariablesBehindIt() {
        files(Map.of("dataflow.url", "jdbc:postgresql://${env.host}:${POSTGRES_PORT}/db", "env.host", "${POSTGRES_HOST}"));
        variables(Map.of("POSTGRES_HOST", "postgres", "POSTGRES_PORT", "5432"));

        assertThat(origin.of("dataflow.url")).isEqualTo("Environment POSTGRES_HOST, POSTGRES_PORT");
    }

    @Test
    void aPlaceholderFallingBackToItsDefaultStaysInTheFile() {
        files(Map.of("dataflow.flag", "${SOME_FLAG:false}"));

        assertThat(origin.of("dataflow.flag")).isEqualTo("application-docker.yml");
    }

    @Test
    void aVariableNamedAfterTheSettingOverridesTheFile() {
        files(Map.of("dataflow.max-rows", "5"));
        variables(Map.of("DATAFLOW_MAX_ROWS", "9"));

        assertThat(origin.of("dataflow.max-rows")).isEqualTo("Environment DATAFLOW_MAX_ROWS");
    }

    @Test
    void anUnsetSettingHasNoOrigin() {
        assertThat(origin.of("dataflow.nothing")).isNull();
    }

    @Test
    void aPlaceholderLoopEnds() {
        files(Map.of("a", "${b}", "b", "${a}"));

        assertThat(origin.of("a")).isEqualTo("application-docker.yml");
    }
}
