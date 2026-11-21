package com.dataflow.workspace.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityLibrariesTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "net.minidev.json.JSONObject",
                "com.nimbusds.oauth2.sdk.token.BearerTokenError",
                "com.ulisesbocchio.jasyptspringboot.encryptor.SimpleGCMConfig"
            })
    void unpatchedIntegrationsAreAbsent(String type) {
        assertThat(ClassUtils.isPresent(type, getClass().getClassLoader())).isFalse();
    }
}
