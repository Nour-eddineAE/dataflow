package com.dataflow.config;

import org.jasypt.encryption.StringEncryptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class EncryptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StringEncryptor.class)
    public StringEncryptor jasyptStringEncryptor(Environment environment) {
        return new ConfiguredStringEncryptor(environment);
    }
}
