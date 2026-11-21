package com.dataflow.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedPropertiesTest {

    private final Properties fixture = fixture();
    private final EncryptedPropertiesEnvironmentPostProcessor processor =
            new EncryptedPropertiesEnvironmentPostProcessor();

    private static ConfigurableEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(new MapPropertySource("test", properties));
        return environment;
    }

    private static Properties fixture() {
        try {
            return PropertiesLoaderUtils.loadProperties(new ClassPathResource("encryption-fixture.properties"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void decrypt_readsCiphertextFromTheExistingCli() {
        ConfigurableEnvironment environment =
                environment(Map.of("jasypt.encryptor.password", value("password"), "secret", encrypted()));

        process(environment);

        assertThat(environment.getProperty("secret")).isEqualTo(value("plaintext"));
        assertThat(((MapPropertySource)
                                environment.getPropertySources().get("test").getSource())
                        .getProperty("secret"))
                .isEqualTo(encrypted());
    }

    @Test
    void encrypt_remainsCompatibleWithTheExistingCoreSettings() {
        StringEncryptor encryptor =
                new ConfiguredStringEncryptor(environment(Map.of("jasypt.encryptor.password", value("password"))));
        StandardPBEStringEncryptor existing = new StandardPBEStringEncryptor();
        existing.setPassword(value("password"));
        existing.setAlgorithm(value("algorithm"));
        existing.setIvGenerator(new RandomIvGenerator());
        existing.setKeyObtentionIterations(1000);
        existing.setStringOutputType("base64");

        String ciphertext = encryptor.encrypt(value("plaintext"));

        assertThat(existing.decrypt(ciphertext)).isEqualTo(value("plaintext"));
        assertThat(encryptor.decrypt(existing.encrypt(value("plaintext")))).isEqualTo(value("plaintext"));
        assertThat(encryptor.encrypt(value("plaintext"))).isNotEqualTo(ciphertext);
    }

    @Test
    void decrypt_requiresAMasterKeyOnlyWhenAnEncryptedValueIsRead() {
        ConfigurableEnvironment environment = environment(Map.of("secret", encrypted(), "plain", value("plaintext")));

        process(environment);

        assertThat(environment.getProperty("plain")).isEqualTo(value("plaintext"));
        assertThatThrownBy(() -> environment.getProperty("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JASYPT_ENCRYPTOR_PASSWORD is required")
                .hasMessageNotContaining(value("ciphertext"));
    }

    @Test
    void decrypt_rejectsAWrongMasterKeyWithoutReturningTheCiphertext() {
        ConfigurableEnvironment environment =
                environment(Map.of("jasypt.encryptor.password", "wrong-key", "secret", encrypted()));

        process(environment);

        assertThatThrownBy(() -> environment.getProperty("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot decrypt secret")
                .hasMessageNotContaining(value("ciphertext"))
                .hasMessageNotContaining("wrong-key");
    }

    @Test
    void properties_preservePrecedenceWithoutReadingShadowedSecrets() {
        ConfigurableEnvironment environment = environment(Map.of("secret", encrypted()));
        environment.getPropertySources().addFirst(new MapPropertySource("override", Map.of("secret", "override")));
        List<String> order = environment.getPropertySources().stream()
                .map(PropertySource::getName)
                .toList();

        process(environment);

        assertThat(environment.getProperty("secret")).isEqualTo("override");
        assertThat(environment.getPropertySources().stream()
                        .map(PropertySource::getName)
                        .toList())
                .isEqualTo(order);
    }

    @Test
    void properties_decryptTheHighestPriorityValue() {
        ConfigurableEnvironment environment =
                environment(Map.of("secret", "default", "jasypt.encryptor.password", value("password")));
        environment.getPropertySources().addFirst(new MapPropertySource("override", Map.of("secret", encrypted())));

        process(environment);

        assertThat(environment.getProperty("secret")).isEqualTo(value("plaintext"));
    }

    @Test
    void environmentVariables_keepRelaxedBindingAndMasterKeyLookup() {
        ConfigurableEnvironment environment = environment(Map.of());
        environment
                .getPropertySources()
                .addFirst(new SystemEnvironmentPropertySource(
                        "systemEnvironment",
                        Map.of("DATAFLOW_ENGINE_SECRET", encrypted(), "JASYPT_ENCRYPTOR_PASSWORD", value("password"))));
        ConfigurationPropertySources.attach(environment);

        process(environment);

        assertThat(Binder.get(environment)
                        .bind("dataflow.engine", Bindable.of(Engine.class))
                        .get()
                        .secret())
                .isEqualTo(value("plaintext"));
        assertThat(environment.getProperty("dataflow.engine.secret")).isEqualTo(value("plaintext"));
    }

    @Test
    void environmentVariables_rejectMissingKeysBeforeBinding() {
        ConfigurableEnvironment environment = environment(Map.of());
        environment
                .getPropertySources()
                .addFirst(new SystemEnvironmentPropertySource(
                        "systemEnvironment", Map.of("DATAFLOW_ENGINE_SECRET", encrypted())));

        assertThatThrownBy(() -> process(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JASYPT_ENCRYPTOR_PASSWORD is required");
    }

    @Test
    void environmentVariables_rejectWrongKeysBeforeBinding() {
        ConfigurableEnvironment environment = environment(Map.of());
        environment
                .getPropertySources()
                .addFirst(new SystemEnvironmentPropertySource(
                        "systemEnvironment",
                        Map.of("DATAFLOW_ENGINE_SECRET", encrypted(), "JASYPT_ENCRYPTOR_PASSWORD", "wrong-key")));

        assertThatThrownBy(() -> process(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot decrypt secret");
    }

    @Test
    void placeholders_resolveTheEncryptedValueAndItsSettings() {
        ConfigurableEnvironment environment = environment(Map.of(
                "jasypt.encryptor.password",
                value("password"),
                "jasypt.encryptor.algorithm",
                "${encryption.algorithm}",
                "encryption.algorithm",
                value("algorithm"),
                "jasypt.encryptor.iv-generator-classname",
                "${encryption.iv}",
                "encryption.iv",
                value("iv-generator"),
                "LAKE_SECRET_KEY",
                encrypted(),
                "dataflow.engine.secret",
                "${LAKE_SECRET_KEY}"));

        process(environment);

        assertThat(environment.getProperty("dataflow.engine.secret")).isEqualTo(value("plaintext"));
    }

    @Test
    void placeholders_canSupplyTheCiphertext() {
        ConfigurableEnvironment environment = environment(Map.of(
                "jasypt.encryptor.password",
                value("password"),
                "secret",
                "ENC(${ciphertext})",
                "ciphertext",
                value("ciphertext")));

        process(environment);

        assertThat(environment.getProperty("secret")).isEqualTo(value("plaintext"));
    }

    @Test
    void placeholders_resolveInsideDecryptedValues() {
        ConfigurableEnvironment environment = environment(Map.of(
                "jasypt.encryptor.password",
                value("password"),
                "secret",
                "ENC(" + value("placeholder-ciphertext") + ")",
                "service.host",
                "service.test"));

        process(environment);

        assertThat(environment.getProperty("secret")).isEqualTo("service.test");
    }

    @Test
    void properties_supportSourcesWithoutEnumerableNames() {
        ConfigurableEnvironment environment = environment(Map.of("jasypt.encryptor.password", value("password")));
        environment.getPropertySources().addFirst(new PropertySource<>("custom") {
            @Override
            public @Nullable Object getProperty(String name) {
                return "secret".equals(name) ? encrypted() : null;
            }
        });

        process(environment);

        assertThat(environment.getProperty("secret")).isEqualTo(value("plaintext"));
    }

    @Test
    void boot_loadsTheRegisteredProcessorAndInjectableEncryptor() {
        try (ConfigurableApplicationContext context = SpringApplication.run(
                TestApplication.class, "--spring.config.location=classpath:encryption-application.properties")) {
            assertThat(context.getEnvironment().getProperty("dataflow.engine.secret"))
                    .isEqualTo(value("plaintext"));
            assertThat(context.getBean(Engine.class).secret()).isEqualTo(value("plaintext"));
            assertThat(context.getBean(StringEncryptor.class).decrypt(value("ciphertext")))
                    .isEqualTo(value("plaintext"));
        }
    }

    private void process(ConfigurableEnvironment environment) {
        processor.postProcessEnvironment(environment, new SpringApplication(TestApplication.class));
    }

    private String encrypted() {
        return "ENC(" + value("ciphertext") + ")";
    }

    private String value(String key) {
        return Objects.requireNonNull(fixture.getProperty(key));
    }

    @ConfigurationProperties("dataflow.engine")
    record Engine(String secret) {}

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableConfigurationProperties(Engine.class)
    static class TestApplication {}
}
