package com.dataflow.engine.transform;

import com.dataflow.engine.config.EngineProperties;
import com.dataflow.engine.core.transform.RunLake;
import com.dataflow.engine.core.transform.Steps;
import com.dataflow.engine.core.transform.TransformationSteps;
import com.dataflow.engine.release.LoadedProject;
import com.dataflow.spec.compile.CompiledTransformations;
import com.dataflow.spec.compile.TransformationCompiler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.SparkSession;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class Transformations {

    private final LoadedProject project;
    private final String drafts;
    private final ApplicationContext context;
    private final ObjectMapper json = new ObjectMapper();
    private CompiledTransformations compiled;

    public Transformations(LoadedProject project, EngineProperties properties, ApplicationContext context) {
        this.project = project;
        this.drafts = properties.tasks().transformations();
        this.context = context;
    }

    public Steps steps(SparkSession spark, RunLake reads, boolean needed) {
        if (!needed) {
            return Steps.NONE;
        }
        CompiledTransformations definitions = compiled();
        if (definitions.artifact() != null) {
            spark.sparkContext().addJar(definitions.artifact().toUri().toString());
        }
        return new TransformationSteps(
                spark, new SpringTransformationCatalog(context, definitions), reads);
    }

    private synchronized CompiledTransformations compiled() {
        if (compiled == null) {
            Map<String, String> sources = new TreeMap<>(TransformationCompiler.read(project.root()));
            sources.putAll(drafts());
            long started = System.nanoTime();
            compiled = new TransformationCompiler().compile(sources, getClass().getClassLoader());
            log.info(
                    "Compiled {} transformation source(s) in {} ms: {}",
                    sources.size(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                    compiled.byId().keySet());
        }
        return compiled;
    }

    private Map<String, String> drafts() {
        if (drafts == null || drafts.isBlank()) {
            return Map.of();
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(drafts), StandardCharsets.UTF_8);
            return json.readValue(decoded, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("ENGINE_TRANSFORMATIONS is not base64 JSON", e);
        }
    }
}
