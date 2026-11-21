package com.dataflow.engine.config;

import com.dataflow.engine.core.flows.FlowGraphs;
import com.dataflow.engine.core.flows.Sources;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.serving.ClickHouseHttp;
import com.dataflow.engine.core.serving.ServingDdl;
import com.dataflow.engine.core.serving.ServingSettings;
import com.dataflow.engine.core.serving.Warehouse;
import com.dataflow.engine.core.tower.PipesTower;
import com.dataflow.engine.core.tower.TowerReporter;
import com.dataflow.engine.release.ActiveRelease;
import com.dataflow.engine.release.LoadedProject;
import com.dataflow.engine.release.ReleaseFiles;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.PipelineSpecs;
import com.dataflow.spec.Project;
import com.dataflow.spec.ProjectLoader;
import com.dataflow.spec.ReleaseId;
import com.dataflow.spec.lake.Lake;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class EngineConfiguration {

    private static final String EVENT_LOG_DIR = "spark.eventLog.dir";

    private static LoadedProject released(PipelineSpecLoader loader, ReleaseFiles files, ReleaseId id) {
        Path root = files.materialize(id);
        Project project = new ProjectLoader(loader).load(root);
        if (!project.name().equals(id.project())) {
            throw new IllegalStateException("Release " + id + " holds project '" + project.name() + "'");
        }
        return new LoadedProject(root, project.pipelines());
    }

    @Bean
    public ActiveRelease activeRelease(ApplicationArguments args) {
        return ActiveRelease.of(args);
    }

    @Bean
    public ReleaseFiles releaseFiles(EngineProperties properties, SparkProperties spark) {
        return new ReleaseFiles(properties.releasesRoot(), spark.settings(properties.releasesRoot()));
    }

    @Bean
    public LoadedProject loadedProject(EngineProperties properties, ActiveRelease release, ReleaseFiles files) {
        PipelineSpecLoader loader = new PipelineSpecLoader();
        return release.id().map(id -> released(loader, files, id)).orElseGet(() -> {
            Path folder = Path.of(properties.pipelinesLocation()).toAbsolutePath();
            return Files.isDirectory(folder)
                    ? new LoadedProject(folder.getParent(), loader.loadAll(folder))
                    : new LoadedProject(folder.getParent(), new PipelineSpecs(Map.of()));
        });
    }

    @Bean
    public PipelineSpecs pipelineSpecs(LoadedProject project) {
        return project.pipelines();
    }

    @Bean(destroyMethod = "stop")
    @Lazy
    public SparkSession sparkSession(
            SparkProperties properties, EngineProperties engine, PipelineSpecs plans, ApplicationArguments args) {
        SparkConf conf = new SparkConf().setAppName(properties.appName());
        properties
                .settings(
                        engine.lakeRoot(),
                        engine.releasesRoot(),
                        properties.config().get(EVENT_LOG_DIR))
                .forEach(conf::set);
        if (args.containsOption("dataset")) {
            plans.all().stream()
                    .filter(plan -> args.getOptionValues("dataset").contains(plan.dataset()))
                    .findFirst()
                    .ifPresent(plan -> plan.compute().sparkConf().forEach(conf::set));
        }
        return SparkSession.builder().config(conf).getOrCreate();
    }

    @Bean
    public Lake lake(EngineProperties properties) {
        return new Lake(properties.lakeRoot());
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "close")
    public TowerReporter towerReporter() {
        return PipesTower.fromEnvironment(System.getenv());
    }

    @Bean
    public FlowGraphs flowGraphs(EngineProperties properties) {
        EngineProperties.Serving serving = properties.serving();
        Warehouse warehouse =
                serving.enabled() ? new ClickHouseHttp(serving.url(), serving.user(), serving.password(), serving.timeouts()) : null;
        EngineProperties.Maintenance maintenance = properties.maintenance();
        return new FlowGraphs(
                new Sources(Path.of(properties.cacheDir()), properties.sources().timeouts()),
                warehouse,
                new ServingDdl(
                        new ServingSettings(serving.database(), serving.collection(), serving.refreshEvery()),
                        properties.lakeRoot()),
                maintenance.enabled()
                        ? new FlowGraphs.Maintenance(maintenance.interval(), maintenance.retentionHours())
                        : null);
    }

    @Bean
    @Lazy
    public DeltaTables deltaTables(SparkSession spark) {
        return new DeltaTables(spark);
    }
}
