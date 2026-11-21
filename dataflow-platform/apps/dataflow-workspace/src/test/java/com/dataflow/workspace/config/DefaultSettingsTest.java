package com.dataflow.workspace.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSettingsTest {

    private static Binder defaults() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment);
    }

    @Test
    void httpTimeoutsBind() throws IOException {
        HttpProperties http = defaults().bind("dataflow.workspace.http", HttpProperties.class).get();

        assertThat(http.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(http.probeTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void workspaceSettingsBind() throws IOException {
        WorkspaceProperties workspace = defaults().bind("dataflow.workspace", WorkspaceProperties.class).get();

        assertThat(workspace.tower().maxOutput()).isEqualTo(DataSize.ofMegabytes(4));
        assertThat(workspace.serving().dashboardRows()).isEqualTo(5000);
        assertThat(workspace.alerts().timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(workspace.git().github().timeout()).isEqualTo(Duration.ofSeconds(15));
    }
}
