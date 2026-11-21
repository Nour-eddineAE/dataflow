package com.dataflow.workspace.releases;

import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.ProjectSpec;
import com.dataflow.spec.ReleaseId;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.releases.ReleaseFiles.Manifest;
import com.dataflow.workspace.releases.ReleaseFiles.Pointer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Slf4j
@Component
public class DeployedSpecs implements DeployedView {

    private static final String VIEWS = "views";

    private final ReleaseStore store;
    private final ObjectMapper json;
    private final Path cache;
    private final String target;
    private final AtomicReference<View> current;

    public DeployedSpecs(ReleaseStore store, ObjectMapper json, WorkspaceProperties properties) {
        this.store = store;
        this.json = json;
        this.cache = Path.of(properties.releases().cacheDir()).toAbsolutePath().normalize();
        this.target = properties.releases().target();
        this.current = new AtomicReference<>(
                new View(directory(cache.resolve(VIEWS).resolve("empty")), Map.of(), List.of()));
    }

    private static List<Path> specs(Path pipelines) {
        if (!Files.isDirectory(pipelines)) {
            return List.of();
        }
        try (Stream<Path> folders = Files.list(pipelines)) {
            return folders.map(folder -> folder.resolve(PipelineSpecLoader.PLAN_FILE))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copy(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, byte[] content) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path directory(Path path) {
        try {
            return Files.createDirectories(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Path root() {
        return current.get().root();
    }

    public String target() {
        return target;
    }

    @Override
    public Optional<ReleaseId> releaseOf(String dataset) {
        return Optional.ofNullable(current.get().datasets().get(dataset));
    }

    public Map<String, ReleaseId> datasets() {
        return current.get().datasets();
    }

    public List<Pointer> releases() {
        return current.get().releases();
    }

    @Scheduled(fixedDelayString = "${dataflow.workspace.releases.refresh-every}", initialDelay = 0)
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.warn("Cannot read the current releases of {}: {}", target, e.getMessage());
        }
    }

    public synchronized void refresh() {
        List<Pointer> pointers = store.projects().stream()
                .map(project -> pointer(project, target))
                .flatMap(Optional::stream)
                .toList();
        View previous = current.get();
        if (pointers.equals(previous.releases())) {
            return;
        }
        Path view = directory(cache.resolve(VIEWS).resolve(UUID.randomUUID().toString()));
        Map<String, ReleaseId> datasets = new TreeMap<>();
        for (Pointer pointer : pointers) {
            Path pipelines = materialize(pointer.id()).resolve(ProjectSpec.PIPELINES);
            for (Path spec : specs(pipelines)) {
                String dataset = spec.getParent().getFileName().toString();
                if (datasets.containsKey(dataset)) {
                    log.warn(
                            "Dataset {} of {} is already released by {}; skipped",
                            dataset,
                            pointer.id(),
                            datasets.get(dataset));
                    continue;
                }
                copy(spec, view.resolve(dataset).resolve(PipelineSpecLoader.PLAN_FILE));
                datasets.put(dataset, pointer.id());
            }
        }
        current.set(new View(view, Map.copyOf(datasets), pointers));
        log.info(
                "Deployed specs of {}: {} from {}",
                target,
                datasets.keySet(),
                pointers.stream().map(Pointer::id).toList());
        prune(previous.root(), view);
    }

    @Override
    public Optional<String> releasedFile(String dataset, String path) {
        return releaseOf(dataset)
                .map(this::materialize)
                .map(root -> root.resolve(path).normalize())
                .filter(Files::isRegularFile)
                .map(file -> {
                    try {
                        return Files.readString(file);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    public Optional<Pointer> pointer(String project, String target) {
        return store.get(ReleaseFiles.pointer(project, target)).map(bytes -> read(bytes, Pointer.class));
    }

    public Path materialize(ReleaseId release) {
        Path folder = cache.resolve("releases").resolve(release.project()).resolve(release.commit());
        if (Files.isRegularFile(folder.resolve(ReleaseId.MANIFEST))) {
            return folder;
        }
        byte[] raw = store.get(ReleaseFiles.manifest(release))
                .orElseThrow(() -> new IllegalStateException("Release " + release + " is missing or incomplete"));
        Manifest manifest = read(raw, Manifest.class);
        for (String file : manifest.files()) {
            byte[] content = store.get(ReleaseFiles.file(release, file))
                    .orElseThrow(() -> new IllegalStateException("Release " + release + " lacks " + file));
            write(folder.resolve(file), content);
        }
        write(folder.resolve(ReleaseId.MANIFEST), raw);
        return folder;
    }

    private <T> T read(byte[] bytes, Class<T> type) {
        try {
            return json.readValue(bytes, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Unreadable " + type.getSimpleName(), e);
        }
    }

    private void prune(Path previous, Path kept) {
        Path views = cache.resolve(VIEWS);
        try (Stream<Path> all = Files.list(views)) {
            List<Path> stale = new ArrayList<>(all.filter(view -> !view.equals(previous)
                            && !view.equals(kept)
                            && !view.getFileName().toString().equals("empty"))
                    .toList());
            stale.sort(Comparator.naturalOrder());
            for (Path view : stale) {
                try (Stream<Path> files = Files.walk(view)) {
                    files.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Cannot delete " + path, e);
                        }
                    });
                }
            }
        } catch (UncheckedIOException | IOException e) {
            log.warn("Cannot prune old views under {}: {}", views, e.getMessage());
        }
    }

    private record View(Path root, Map<String, ReleaseId> datasets, List<Pointer> releases) {}
}
