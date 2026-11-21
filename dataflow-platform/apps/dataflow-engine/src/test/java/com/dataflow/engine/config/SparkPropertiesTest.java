package com.dataflow.engine.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.support.SpringApplicationJsonEnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SparkPropertiesTest {

    private static final String CHECKPOINTS = "spark.sql.streaming.checkpointFileManagerClass";
    private static final String S3_IMPL = "spark.hadoop.fs.s3a.impl";
    private static final String AZURE_PROVIDER = "spark.hadoop.fs.azure.account.oauth.provider.type";
    private static final String GCS_IMPL = "spark.hadoop.fs.gs.impl";

    private static final String SHUFFLE = "spark.sql.shuffle.partitions";
    private static final String RUN_COMPUTE = "{\"dataflow.spark.config[" + SHUFFLE + "]\": \"16\"}";

    private final SparkProperties spark = bind("application-spark.yml");

    private static SparkProperties bind(String profile) {
        return bind(profile, new StandardEnvironment());
    }

    private static SparkProperties bind(String profile, StandardEnvironment environment) {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        try {
            for (String file : new String[] {profile, "application-spark-common.yml", "application.yml"}) {
                for (PropertySource<?> source : loader.load(file, new ClassPathResource(file))) {
                    environment.getPropertySources().addLast(source);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return Binder.get(environment)
                .bind("dataflow.spark", SparkProperties.class)
                .get();
    }

    @Test
    void anS3Lake_getsTheS3SettingsAndAbortableCheckpoints() {
        Map<String, String> settings = spark.settings("s3a://lake", "s3a://releases");

        assertThat(settings)
                .containsEntry(S3_IMPL, "org.apache.hadoop.fs.s3a.S3AFileSystem")
                .containsEntry("spark.hadoop.fs.s3a.path.style.access", "true")
                .containsKey(CHECKPOINTS)
                .doesNotContainKeys(AZURE_PROVIDER, GCS_IMPL);
    }

    @Test
    void anAzureLake_getsWorkloadIdentityAndPlainCheckpoints() {
        Map<String, String> settings = spark.settings("abfss://lake@acme.dfs.core.windows.net", (String) null);

        assertThat(settings)
                .containsEntry(AZURE_PROVIDER, "org.apache.hadoop.fs.azurebfs.oauth2.WorkloadIdentityTokenProvider")
                .containsEntry("spark.master", "local[*]")
                .doesNotContainKeys(S3_IMPL, CHECKPOINTS);
    }

    @Test
    void aGoogleLake_withS3Releases_getsBothStores() {
        Map<String, String> settings = spark.settings("gs://lake", "s3a://releases");

        assertThat(settings)
                .containsKeys(GCS_IMPL, S3_IMPL)
                .containsEntry("spark.hadoop.fs.gs.auth.type", "COMPUTE_ENGINE");
        assertThat(settings).doesNotContainKeys(CHECKPOINTS);
    }

    @Test
    void anAzureLake_withS3EventLogs_keepsPlainCheckpoints() {
        assertThat(spark.settings("abfss://lake@acme.dfs.core.windows.net", "s3a://events"))
                .containsKeys(AZURE_PROVIDER, S3_IMPL)
                .doesNotContainKeys(CHECKPOINTS);
    }

    @Test
    void explicitSparkSettings_overrideStoreDefaults() {
        SparkProperties configured = new SparkProperties(
                "test",
                Map.of(CHECKPOINTS, "custom.Manager", S3_IMPL, "custom.S3"),
                spark.stores(),
                spark.checkpointManagers());

        assertThat(configured.settings("s3a://lake"))
                .containsEntry(CHECKPOINTS, "custom.Manager")
                .containsEntry(S3_IMPL, "custom.S3");
    }

    @Test
    void aLocalLake_getsNoStore() {
        assertThat(spark.settings("file:./lake", "")).doesNotContainKeys(S3_IMPL, AZURE_PROVIDER, GCS_IMPL);
    }

    @Test
    void kubernetesLeavesTheMasterToSparkSubmit() {
        SparkProperties kubernetes = bind("application-k8s.yml");

        assertThat(kubernetes.settings("s3a://lake"))
                .doesNotContainKeys("spark.master")
                .containsEntry("spark.ui.enabled", "true")
                .containsEntry("spark.ui.prometheus.enabled", "true");
    }

    @Test
    void theSparkSettingsOfARun_overrideTheProfile() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("run", Map.of("spring.application.json", RUN_COMPUTE)));
        new SpringApplicationJsonEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertThat(spark.config()).containsEntry(SHUFFLE, "8");
        assertThat(bind("application-spark.yml", environment).config()).containsEntry(SHUFFLE, "16");
    }
}
