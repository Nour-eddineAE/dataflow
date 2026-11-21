package com.dataflow.engine.cli;

import com.dataflow.spec.lake.Lake;
import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CleanTaskTest {

    @TempDir
    Path root;

    private static String encode(String keys) {
        return Base64.getEncoder().encodeToString(keys.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void removesOnlyTheGivenHousekeepingLocations() throws IOException {
        Path preview = write("system/tasks/old.json");
        Path checkpoint = write("system/checkpoints/removed_dataset/silver/offsets/0");
        Path kept = write("system/tasks/new.json");
        Path table = write("history/earthquakes/part-0.parquet");
        Lake lake = new Lake(root.toUri().toString());

        int removed = TaskLauncher.remove(
                lake,
                new Configuration(),
                TaskLauncher.keys(encode("system/tasks/old.json\n\nsystem/checkpoints/removed_dataset/\n")));

        assertThat(removed).isEqualTo(2);
        assertThat(preview).doesNotExist();
        assertThat(checkpoint.getParent().getParent().getParent()).doesNotExist();
        assertThat(kept).exists();
        assertThat(table).exists();
    }

    @Test
    void refusesEverythingIfOneLocationIsNotHousekeeping() throws IOException {
        Path preview = write("system/tasks/old.json");
        Lake lake = new Lake(root.toUri().toString());

        assertThatThrownBy(() -> TaskLauncher.remove(
                        lake, new Configuration(), List.of("system/tasks/old.json", "history/earthquakes/")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(preview).exists();
    }

    private Path write(String key) throws IOException {
        Path file = root.resolve(key);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }
}
