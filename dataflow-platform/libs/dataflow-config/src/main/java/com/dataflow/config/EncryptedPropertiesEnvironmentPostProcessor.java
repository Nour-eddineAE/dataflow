package com.dataflow.config;

import org.jasypt.encryption.StringEncryptor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EncryptedPropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PREFIX = "ENC(";
    private static final String SUFFIX = ")";

    private static Object decrypt(Object value, ConfigurableEnvironment environment, StringEncryptor encryptor) {
        if (value instanceof String text && text.startsWith(PREFIX) && text.endsWith(SUFFIX)) {
            String encrypted = environment.resolveRequiredPlaceholders(text.substring(PREFIX.length(), text.length() - SUFFIX.length()));
            return encryptor.decrypt(encrypted);
        }
        return value;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        StringEncryptor encryptor = new ConfiguredStringEncryptor(environment);
        for (PropertySource<?> source :
                environment.getPropertySources().stream().toList()) {
            if ("configurationProperties".equals(source.getName())) {
                continue;
            }
            PropertySource<?> encrypted;
            if (source instanceof SystemEnvironmentPropertySource variables) {
                Map<String, Object> values = new LinkedHashMap<>();
                variables
                        .getSource()
                        .forEach((name, value) -> values.put(name, decrypt(value, environment, encryptor)));
                encrypted = new SystemEnvironmentPropertySource(source.getName(), values);
            } else if (source instanceof EnumerablePropertySource<?> enumerable) {
                encrypted = new EncryptedEnumerable(enumerable, environment, encryptor);
            } else {
                encrypted = new EncryptedSource(source, environment, encryptor);
            }
            environment.getPropertySources().replace(source.getName(), encrypted);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private static final class EncryptedEnumerable extends EnumerablePropertySource<EnumerablePropertySource<?>> {

        private final ConfigurableEnvironment environment;
        private final StringEncryptor encryptor;

        private EncryptedEnumerable(
                EnumerablePropertySource<?> source, ConfigurableEnvironment environment, StringEncryptor encryptor) {
            super(source.getName(), source);
            this.environment = environment;
            this.encryptor = encryptor;
        }

        @Override
        public String[] getPropertyNames() {
            return source.getPropertyNames();
        }

        @Override
        public @Nullable Object getProperty(String name) {
            Object value = source.getProperty(name);
            return value == null ? null : decrypt(value, environment, encryptor);
        }
    }

    private static final class EncryptedSource extends PropertySource<PropertySource<?>> {

        private final ConfigurableEnvironment environment;
        private final StringEncryptor encryptor;

        private EncryptedSource(
                PropertySource<?> source, ConfigurableEnvironment environment, StringEncryptor encryptor) {
            super(source.getName(), source);
            this.environment = environment;
            this.encryptor = encryptor;
        }

        @Override
        public @Nullable Object getProperty(String name) {
            Object value = source.getProperty(name);
            return value == null ? null : decrypt(value, environment, encryptor);
        }
    }
}
