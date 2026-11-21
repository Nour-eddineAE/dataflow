package com.dataflow.workspace.specs;

import com.dataflow.spec.InvalidPipelineSpecException;
import com.dataflow.spec.Names;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.PipelineSpecs;
import com.dataflow.workspace.releases.DeployedView;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

@Component
public class SpecStore {


    private final DeployedView deployed;
    private final PipelineSpecLoader loader;
    private final Parsing parsing;

    public SpecStore(DeployedView deployed, PipelineSpecLoader loader, Parsing parsing) {
        this.deployed = deployed;
        this.loader = loader;
        this.parsing = parsing;
    }

    private static Path file(Path root, String dataset) {
        if (dataset == null || !Names.DATASET.matcher(dataset).matches()) {
            throw new UnknownSpecException(dataset);
        }
        Path file = root.resolve(dataset).resolve(PipelineSpecLoader.PLAN_FILE).normalize();
        if (!file.startsWith(root)) {
            throw new UnknownSpecException(dataset);
        }
        return file;
    }

    private static SpecCheck failed(String content, List<String> problems) {
        return new SpecCheck(
                null,
                problems.stream()
                        .map(problem -> ProblemLocator.locate(content, problem))
                        .toList());
    }

    private static String read(Path root, Path file) {
        try {
            Path deployed = root.toRealPath();
            Path resolved = file.toRealPath();
            if (!resolved.startsWith(deployed)) {
                throw new IllegalArgumentException("Spec path must stay inside the deployed pipelines directory");
            }
            return Files.readString(resolved);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    public Parsing parsing() {
        return parsing;
    }

    public Path root() {
        return deployed.root();
    }

    public List<StoredSpec> all() {
        Path root = root();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> folders = Files.list(root)) {
            return folders.filter(Files::isDirectory)
                    .map(folder -> folder.getFileName().toString())
                    .filter(name -> Names.DATASET.matcher(name).matches())
                    .sorted()
                    .map(this::find)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + root, e);
        }
    }

    public List<PipelineSpec> valid() {
        return all().stream().filter(StoredSpec::valid).map(StoredSpec::spec).toList();
    }

    public StoredSpec get(String dataset) {
        return find(dataset).orElseThrow(() -> new UnknownSpecException(dataset));
    }

    public PipelineSpec spec(String dataset) {
        StoredSpec stored = get(dataset);
        if (!stored.valid()) {
            throw new InvalidDraftException(dataset, stored.problems());
        }
        return stored.spec();
    }

    public Optional<StoredSpec> find(String dataset) {
        if (dataset == null || !Names.DATASET.matcher(dataset).matches()) {
            return Optional.empty();
        }
        Path root = root().toAbsolutePath().normalize();
        Path file = file(root, dataset);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String content = read(root, file);
        SpecCheck check = parse(content, dataset);
        return Optional.of(new StoredSpec(dataset, file, content, check.spec(), check.problems()));
    }

    public Map<String, Object> tree(String content) {
        try {
            return loader.tree(content, "draft");
        } catch (InvalidPipelineSpecException e) {
            throw new IllegalArgumentException(String.join("; ", e.problems()), e);
        }
    }

    public SpecCheck check(String content, String dataset) {
        SpecCheck parsed = parse(content, dataset);
        if (!parsed.valid()) {
            return parsed;
        }
        List<String> clashes = clashes(parsed.spec());
        return clashes.isEmpty() ? parsed : failed(content, clashes);
    }

    private SpecCheck parse(String content, String dataset) {
        PipelineSpec spec;
        try {
            spec = parsing.run(() -> loader.parse(content, dataset == null ? "draft" : dataset));
        } catch (InvalidPipelineSpecException e) {
            return failed(content, e.problems());
        } catch (RuntimeException e) {
            return failed(content, List.of(String.valueOf(e.getMessage())));
        }
        List<String> problems = new ArrayList<>();
        if (dataset != null && !dataset.equals(spec.dataset())) {
            problems.add("dataset '" + spec.dataset() + "' must match its folder name '" + dataset + "'");
        }
        return problems.isEmpty() ? new SpecCheck(spec, List.of()) : failed(content, problems);
    }

    public Path file(String dataset) {
        return file(root().toAbsolutePath().normalize(), dataset);
    }

    private List<String> clashes(PipelineSpec draft) {
        Map<String, PipelineSpec> specs = new TreeMap<>();
        all().stream().filter(StoredSpec::valid).forEach(stored -> specs.put(stored.dataset(), stored.spec()));
        specs.put(draft.dataset(), draft);
        try {
            new PipelineSpecs(specs);
            return List.of();
        } catch (IllegalStateException e) {
            return List.of(e.getMessage());
        }
    }
}
