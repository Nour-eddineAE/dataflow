package com.dataflow.workspace.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageAccessPropertiesTest {

    @Test
    void trailingSlashesAreNormalizedBeforeBuildingConsoleLinks() {
        StorageAccessProperties settings = settings("http://rustfs:9000/", "https://storage.example/", List.of());

        assertThat(settings.endpoint()).isEqualTo("http://rustfs:9000");
        assertThat(settings.browserUrl()).isEqualTo("https://storage.example");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://storage/path", "http://storage/?token=secret", "http://storage/#fragment",
            "http://user:secret@storage", "ftp://storage", "http://storage:99999"})
    void endpointAndBrowserMustBeOrigins(String url) {
        assertThatThrownBy(() -> settings(url, "http://console", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings("http://rustfs", url, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledStorageDoesNotRequireEndpointsOrCredentials() {
        StorageAccessProperties settings = new StorageAccessProperties(false, null, null, null, null, null);

        assertThat(settings.legacyPolicies()).isEmpty();
    }

    private static StorageAccessProperties settings(String endpoint, String browser, List<String> legacy) {
        return new StorageAccessProperties(true, endpoint, "lake", browser, Duration.ofSeconds(2), legacy);
    }
}
