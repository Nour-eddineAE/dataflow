package com.dataflow.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;

final class ConfiguredStringEncryptor implements StringEncryptor {

    private static final String ALGORITHM = "PBEWITHHMACSHA512ANDAES_256";
    private static final String IV_GENERATOR = "org.jasypt.iv.RandomIvGenerator";
    private static final int ITERATIONS = 1000;
    private static final String OUTPUT_TYPE = "base64";

    private final Environment environment;
    private @Nullable StandardPBEStringEncryptor delegate;

    ConfiguredStringEncryptor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public @Nullable String encrypt(@Nullable String value) {
        return value == null ? null : delegate().encrypt(value);
    }

    @Override
    public @Nullable String decrypt(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return delegate().decrypt(value);
        } catch (EncryptionOperationNotPossibleException e) {
            throw new IllegalStateException(
                    "Cannot decrypt secret; verify JASYPT_ENCRYPTOR_PASSWORD and encryption settings", e);
        }
    }

    private synchronized StandardPBEStringEncryptor delegate() {
        if (delegate == null) {
            String password = environment.getProperty("jasypt.encryptor.password");
            if (password == null) {
                password = environment.getProperty("JASYPT_ENCRYPTOR_PASSWORD");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("JASYPT_ENCRYPTOR_PASSWORD is required for encrypted secrets");
            }
            SimpleStringPBEConfig config = new SimpleStringPBEConfig();
            config.setPassword(password);
            config.setAlgorithm(environment.getProperty("jasypt.encryptor.algorithm", ALGORITHM));
            config.setIvGeneratorClassName(environment.getProperty(
                    "jasypt.encryptor.iv-generator-classname", IV_GENERATOR));
            config.setKeyObtentionIterations(ITERATIONS);
            config.setStringOutputType(OUTPUT_TYPE);
            StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
            encryptor.setConfig(config);
            encryptor.initialize();
            delegate = encryptor;
        }
        return delegate;
    }
}
