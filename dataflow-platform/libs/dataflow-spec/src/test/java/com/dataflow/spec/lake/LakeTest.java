package com.dataflow.spec.lake;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LakeTest {

    @Test
    void uploadsOf_givesEachPersonAFolderOfSafeCharacters() {
        assertThat(Lake.uploadsOf("ada")).isEqualTo("uploads/ada/");
        assertThat(Lake.uploadsOf("ada@corp/../bob")).isEqualTo("uploads/ada_corp___bob/");
    }

    @Test
    void eventsDataset_readsTheDatasetOfAnEventLogKeyOnly() {
        assertThat(Lake.eventsDataset(Lake.eventsKey("earthquakes"))).contains("earthquakes");
        assertThat(Lake.eventsDataset("system/checkpoints/earthquakes/")).isEmpty();
        assertThat(Lake.eventsDataset("history/earthquakes/")).isEmpty();
        assertThat(Lake.eventsDataset("system/events/earthquakes/part/")).isEmpty();
    }

    private final Lake lake = new Lake("s3a://lake");

    @Test
    void file_acceptsAKeyOrAPathUnderTheRoot() {
        assertThat(lake.file("inbox/sensors/readings_2026-03-04.csv"))
                .isEqualTo("s3a://lake/inbox/sensors/readings_2026-03-04.csv");
        assertThat(lake.file("s3a://lake/landing/earthquakes/all_month.1a2b.csv"))
                .isEqualTo("s3a://lake/landing/earthquakes/all_month.1a2b.csv");
    }

    @Test
    void file_refusesWhatIsNotAFileOfTheLake() {
        for (String outside : new String[] {
            "../etc/passwd",
            "inbox/../system/tasks/x.json",
            "system/tasks/x.json",
            "s3a://other/x.csv",
            "/etc/passwd",
            "inbox//x.csv"
        }) {
            assertThatThrownBy(() -> lake.file(outside)).as(outside).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void cleanable_allowsOnlyHousekeepingLocations() {
        assertThat(lake.cleanable("system/tasks/0b5c.json")).isEqualTo("s3a://lake/system/tasks/0b5c.json");
        assertThat(lake.cleanable("spark-events/app-1.inprogress"))
                .isEqualTo("s3a://lake/spark-events/app-1.inprogress");
        assertThat(lake.cleanable("system/checkpoints/old_dataset/"))
                .isEqualTo("s3a://lake/system/checkpoints/old_dataset/");
        assertThat(lake.cleanable("uploads/ada/0b5c/"))
                .isEqualTo("s3a://lake/uploads/ada/0b5c/");
        for (String refused : List.of(
                "history/earthquakes/",
                "system/tasks/",
                "system/events/earthquakes/",
                "system/tasks/../../history/x",
                "spark-events",
                "uploads/",
                "/system/tasks/a.json")) {
            assertThatThrownBy(() -> lake.cleanable(refused)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
