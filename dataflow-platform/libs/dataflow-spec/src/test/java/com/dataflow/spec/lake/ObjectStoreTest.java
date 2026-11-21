package com.dataflow.spec.lake;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectStoreTest {

    @Test
    void rootsSelectTheStoreWithoutDependingOnTheHost() {
        assertThat(ObjectStore.of("s3a://lake/prod")).isEqualTo(ObjectStore.S3);
        assertThat(ObjectStore.of("abfss://lake@acme.dfs.core.windows.net/prod"))
                .isEqualTo(ObjectStore.AZURE);
        assertThat(ObjectStore.of("gs://lake/prod")).isEqualTo(ObjectStore.GCS);
        assertThatThrownBy(() -> ObjectStore.of("https://example.com/lake"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported object store");
    }

    @Test
    void prefixesAreRelativeAndHaveExactlyOneTrailingSlash() {
        assertThat(ObjectStore.prefix("s3a://lake")).isEmpty();
        assertThat(ObjectStore.prefix("gs://lake/")).isEmpty();
        assertThat(ObjectStore.prefix("abfss://lake@acme.dfs.core.windows.net/prod/data"))
                .isEqualTo("prod/data/");
        assertThat(ObjectStore.prefix("abfss://lake@acme.dfs.core.windows.net/prod/data/"))
                .isEqualTo("prod/data/");
    }
}
