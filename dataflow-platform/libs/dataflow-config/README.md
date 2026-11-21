# DataFlow Configuration

The shared Spring Boot 4 configuration module decrypts `ENC(...)` properties and supplies the `StringEncryptor` used for
stored Git credentials. It depends on core Jasypt, without the third-party Spring integration.

Decryption runs after Boot loads configuration files. Property sources retain their order, enumerable names, and system
environment matching, so placeholders and relaxed environment binding keep working. Original sources remain unchanged.
Configuration files decrypt when read; environment variables decrypt into a snapshot during processing because Boot
binds directly from their backing map. Missing or incorrect keys for encrypted environment variables fail startup.
Applications without encrypted values can start without a master key; reading or writing an encrypted secret requires
`JASYPT_ENCRYPTOR_PASSWORD`.

Ciphertext remains compatible with `scripts/encrypt.sh` and existing stored credentials: `PBEWITHHMACSHA512ANDAES_256`,
a random salt and IV, 1,000 key iterations, and Base64 output. The existing `jasypt.encryptor.algorithm` and
`jasypt.encryptor.iv-generator-classname` settings remain supported.

The default Maven build has no additional profiles. Run its checks with `mvn -pl libs/dataflow-config -am test` from the
platform root.
