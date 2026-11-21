package com.dataflow.workspace.specs;

import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.Drafts;
import com.dataflow.workspace.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecStoreTest {

    private final Parsing parsing = new Parsing();

    @TempDir
    Path directory;

    private static void writeSpec(Path folder) throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(PipelineSpecLoader.PLAN_FILE), Drafts.text("earthquakes"));
    }

    @AfterEach
    void stop() {
        parsing.stop();
    }

    @Test
    void find_readsARegularSpec() throws IOException {
        Path root = Files.createDirectory(directory.resolve("pipelines"));
        writeSpec(root.resolve("earthquakes"));

        assertThat(store(root).get("earthquakes").content()).isEqualTo(Drafts.text("earthquakes"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "../earthquakes", "/earthquakes", "a/../../earthquakes", "a\\earthquakes"})
    void find_rejectsInvalidDatasetNames(String dataset) {
        SpecStore store = store(directory);

        assertThat(store.find(dataset)).isEmpty();
        assertThatThrownBy(() -> store.file(dataset)).isInstanceOf(UnknownSpecException.class);
    }

    @Test
    void find_rejectsADatasetDirectorySymlinkOutsideTheRoot() throws IOException {
        Path root = Files.createDirectory(directory.resolve("pipelines"));
        Path outside = directory.resolve("pipelines_other");
        writeSpec(outside);
        Files.createSymbolicLink(root.resolve("earthquakes"), outside);

        assertThatThrownBy(() -> store(root).find("earthquakes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inside the deployed pipelines directory");
    }

    @Test
    void find_rejectsASpecFileSymlinkOutsideTheRoot() throws IOException {
        Path root = Files.createDirectory(directory.resolve("pipelines"));
        Path folder = Files.createDirectory(root.resolve("earthquakes"));
        Path outside = directory.resolve("pipelines_other");
        writeSpec(outside);
        Files.createSymbolicLink(
                folder.resolve(PipelineSpecLoader.PLAN_FILE), outside.resolve(PipelineSpecLoader.PLAN_FILE));

        assertThatThrownBy(() -> store(root).find("earthquakes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inside the deployed pipelines directory");
    }

    @Test
    void find_allowsASymlinkToAContainedSpec() throws IOException {
        Path root = Files.createDirectory(directory.resolve("pipelines"));
        Path source = root.resolve("source");
        writeSpec(source);
        Path folder = Files.createDirectory(root.resolve("earthquakes"));
        Files.createSymbolicLink(
                folder.resolve(PipelineSpecLoader.PLAN_FILE), source.resolve(PipelineSpecLoader.PLAN_FILE));

        assertThat(store(root).get("earthquakes").content()).isEqualTo(Drafts.text("earthquakes"));
    }

    private SpecStore store(Path root) {
        return new SpecStore(Workspace.deployed(root), new PipelineSpecLoader(), parsing);
    }
}
