package com.dataflow.workspace.uploads;

import com.dataflow.spec.lake.Lake;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LakeUploadsTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @ParameterizedTest
    @CsvSource({
        "readings.csv, readings.csv",
        "my data (1).csv, my_data_1_.csv",
        "../../etc/passwd, passwd",
        "C:\\Users\\ada\\trips.parquet, trips.parquet",
        "a..b.csv, a.b.csv",
        ".hidden.csv, hidden.csv",
        "été.csv, t_.csv"
    })
    void aFileNameCannotLeaveItsFolder(String original, String safe) {
        assertThat(LakeUploads.safeName(original)).isEqualTo(safe);
    }

    @Test
    void aFileWithNoUsableNameStillGetsOne() {
        assertThat(LakeUploads.safeName(null)).isEqualTo("upload");
        assertThat(LakeUploads.safeName("...")).isEqualTo("upload");
    }

    @Test
    void anUploadLandsInItsOwnersFolderAndIsAValidLakeKey() {
        String key = LakeUploads.key("ada@corp.test", ID, "readings.csv");

        assertThat(key).isEqualTo("uploads/ada_corp.test/" + ID + "/readings.csv");
        assertThat(key).startsWith(Lake.uploadsOf("ada@corp.test"));
        assertThat(Lake.KEY.matcher(key).matches()).isTrue();
    }

    @Test
    void theLakeRootGivesTheBucketAndAnyPrefix() {
        assertThat(LakeUploads.location("s3a://lake", "uploads/a/x.csv"))
                .isEqualTo(new LakeUploads.Location("lake", "uploads/a/x.csv"));
        assertThat(LakeUploads.location("s3://data/dataflow/", "uploads/a/x.csv"))
                .isEqualTo(new LakeUploads.Location("data", "dataflow/uploads/a/x.csv"));
    }

    @Test
    void aLakeThatIsNotOnS3RefusesUploads() {
        assertThatThrownBy(() -> LakeUploads.location("abfss://lake@acct.dfs.core.windows.net", "uploads/a/x.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only to a lake on S3");
    }
}
