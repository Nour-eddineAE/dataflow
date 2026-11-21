package com.dataflow.engine.cli;

import com.dataflow.engine.config.EngineProperties;
import com.dataflow.engine.core.draft.DraftImpact;
import com.dataflow.engine.core.draft.DraftPreview;
import com.dataflow.engine.core.draft.DraftSample;
import com.dataflow.engine.core.draft.Inference;
import com.dataflow.engine.core.draft.Sample;
import com.dataflow.engine.core.draft.SourceInference;
import com.dataflow.engine.core.draft.SourceLocation;
import com.dataflow.engine.core.draft.TaskResults;
import com.dataflow.engine.core.draft.TaskResults.TaskResult;
import com.dataflow.engine.core.flows.Sources;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.tower.TowerReporter;
import com.dataflow.engine.core.transform.RunLake;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.engine.transform.Transformations;
import com.dataflow.spec.Names;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.PipelineSpecs;
import com.dataflow.spec.lake.Lake;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskLauncher {

    private final PipelineSpecs plans;
    private final ObjectProvider<SparkSession> spark;
    private final ObjectProvider<DeltaTables> tables;
    private final Lake lake;
    private final TowerReporter tower;
    private final EngineProperties properties;
    private final Transformations transformations;

    static List<String> keys(String encoded) {
        return encoded == null || encoded.isBlank()
                ? List.of()
                : new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
                        .lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty())
                        .toList();
    }

    static int remove(Lake lake, Configuration hadoop, List<String> keys) throws IOException {
        List<String> locations = keys.stream().map(lake::cleanable).toList();
        int removed = 0;
        for (String location : locations) {
            org.apache.hadoop.fs.Path path = new org.apache.hadoop.fs.Path(location);
            if (path.getFileSystem(hadoop).delete(path, true)) {
                removed++;
            }
        }
        return removed;
    }

    private static Path copyFromLake(SparkSession session, String location, Path folder) {
        org.apache.hadoop.fs.Path file = new org.apache.hadoop.fs.Path(location);
        Path local = folder.resolve(file.getName());
        try {
            Files.createDirectories(folder);
            FileSystem fs = file.getFileSystem(session.sparkContext().hadoopConfiguration());
            try (InputStream in = fs.open(file)) {
                Files.copy(in, local);
            }
            return local;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + location + ": " + e.getMessage(), e);
        }
    }

    private static void remove(Path folder) {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot remove " + path, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot remove " + folder, e);
        }
    }

    public boolean launch(TaskKind kind, String dataset, String task) {
        SparkSession session = spark.getObject();
        String location = lake.task(task);
        String runId = tower.runId().orElse(task);
        try {
            PipelineSpec draft = draft(dataset);
            PipelineSpec stored = plans.all().stream()
                    .filter(plan -> plan.dataset().equals(dataset))
                    .findFirst()
                    .orElse(null);
            boolean needed = draft.usesTransformations() || (stored != null && stored.usesTransformations());
            Steps steps = transformations.steps(session, new RunLake(session, lake, dataset), needed);
            if (!draft.ingests()) {
                Object result = kind == TaskKind.IMPACT
                        ? new DraftImpact(session, lake, tables.getObject(), steps).derived(saved(dataset), draft)
                        : new DraftPreview(session, lake, tables.getObject(), steps).derived(draft);
                TaskResults.write(session, location, TaskResult.done(task, kind.command(), dataset, result));
                log.info("Task {} {} for derived {} written to {}", kind.command(), task, dataset, location);
                return true;
            }
            DraftSample samples =
                    new DraftSample(session, lake, tables.getObject(), new Sources(Path.of(properties.cacheDir()), properties.sources().timeouts()));
            try (Sample sample = samples.take(
                    kind == TaskKind.IMPACT ? saved(dataset) : draft,
                    properties.tasks().sampleRows(),
                    runId)) {
                Object result = kind == TaskKind.PREVIEW
                        ? new DraftPreview(session, lake, tables.getObject(), steps).preview(draft, sample, runId)
                        : new DraftImpact(session, lake, tables.getObject(), steps)
                                .compare(saved(dataset), draft, sample, runId);
                TaskResults.write(session, location, TaskResult.done(task, kind.command(), dataset, result));
            }
            log.info("Task {} {} for {} written to {}", kind.command(), task, dataset, location);
            return true;
        } catch (RuntimeException e) {
            log.error("Task {} {} for {} failed", kind.command(), task, dataset, e);
            TaskResults.write(session, location, TaskResult.failed(task, kind.command(), dataset, e));
            return false;
        }
    }

    public boolean infer(String dataset, String task, String source) {
        SparkSession session = spark.getObject();
        String result = lake.task(task);
        String folder = "_tasks/" + task;
        Path cacheDir = Path.of(properties.cacheDir());
        try {
            boolean web = source.startsWith("http://") || source.startsWith("https://");
            String location = web ? source : lake.file(source);
            Path local = web
                    ? new Sources(cacheDir, properties.sources().timeouts()).download(folder, source)
                    : copyFromLake(session, location, cacheDir.resolve(folder));
            Inference inference = new SourceInference()
                    .infer(
                            session,
                            SourceLocation.analyze(location),
                            local,
                            dataset,
                            properties.tasks().sampleRows());
            TaskResults.write(session, result, TaskResult.done(task, TaskKind.INFER.command(), dataset, inference));
            log.info(
                    "Task infer {} for {} written to {}: {} columns, keys {}",
                    task,
                    dataset,
                    result,
                    inference.columns().size(),
                    inference.keys());
            return true;
        } catch (RuntimeException e) {
            log.error("Task infer {} for {} failed", task, dataset, e);
            TaskResults.write(session, result, TaskResult.failed(task, TaskKind.INFER.command(), dataset, e));
            return false;
        } finally {
            remove(cacheDir.resolve(folder));
        }
    }

    public boolean clean(String task) {
        SparkSession session = spark.getObject();
        String result = lake.task(task);
        try {
            List<String> keys = keys(properties.tasks().clean());
            int removed = remove(lake, session.sparkContext().hadoopConfiguration(), keys);
            log.info("Task clean {} removed {} of {} locations", task, removed, keys.size());
            TaskResults.write(
                    session,
                    result,
                    TaskResult.done(
                            task,
                            TaskKind.CLEAN.command(),
                            Names.PLATFORM,
                            Map.of("requested", keys.size(), "removed", removed)));
            return true;
        } catch (IOException | RuntimeException e) {
            log.error("Task clean {} failed", task, e);
            TaskResults.write(session, result, TaskResult.failed(task, TaskKind.CLEAN.command(), Names.PLATFORM, e));
            return false;
        }
    }

    private PipelineSpec draft(String dataset) {
        String encoded = properties.tasks().draft();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("No draft spec given (ENGINE_DRAFT is empty)");
        }
        String text = new String(Base64.getDecoder().decode(encoded.strip()), StandardCharsets.UTF_8);
        return new PipelineSpecLoader().parse(text, dataset);
    }

    private PipelineSpec saved(String dataset) {
        return plans.all().stream()
                .filter(plan -> plan.dataset().equals(dataset))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(dataset + " has no saved spec to compare with"));
    }
}
