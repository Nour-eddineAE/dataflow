package com.dataflow.workspace.housekeeping;

import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.housekeeping.Candidate.Kind;
import com.dataflow.workspace.releases.InMemoryReleaseStore;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import com.dataflow.workspace.serving.SystemTables;
import com.dataflow.workspace.specs.Parsing;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.tasks.EngineTasks;
import com.dataflow.workspace.tasks.TaskStarted;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class HousekeepingTest {

    private static final List<String> FILE = List.of("_path", "_size", "_time");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);

    private final Parsing parsing = new Parsing();
    private final SpecStore specs = new SpecStore(Workspace.deployed(), new PipelineSpecLoader(), parsing);
    private final InMemoryReleaseStore releases = new InMemoryReleaseStore();
    private final List<List<String>> cleaned = new ArrayList<>();
    private final List<String> people = new ArrayList<>();
    private final RecordingClickHouse serving = new RecordingClickHouse()
            .answer(sql -> sql.contains("'system/tasks/*.json'"), FILE, List.of(
                    List.of("lake/system/tasks/old.json", 1200L, "2026-10-01 08:00:00"),
                    List.of("lake/system/tasks/new.json", 900L, "2026-10-27 08:00:00")))
            .answer(sql -> sql.contains("'spark-events/**'"), FILE, List.of(
                    List.of("lake/spark-events/eventlog_v2_app-1/events_1", 5000L, "2026-09-01 08:00:00"),
                    List.of("lake/spark-events/eventlog_v2_app-1/events_2", 3000L, "2026-09-01 09:00:00"),
                    List.of("lake/spark-events/eventlog_v2_app-2/events_1", 4000L, "2026-10-27 09:00:00"),
                    List.of("lake/spark-events/.keep", 0L, "2026-01-01 00:00:00")))
            .answer(sql -> sql.contains("'system/checkpoints/**'"), FILE, List.of(
                    List.of("lake/system/checkpoints/earthquakes/silver/offsets/0", 300L, "2026-10-27 08:00:00"),
                    List.of("lake/system/checkpoints/old_sensors/silver/offsets/0", 200L, "2026-03-01 08:00:00"),
                    List.of("lake/system/checkpoints/old_sensors/bronze/commits/0", 100L, "2026-03-01 08:00:00")))
            .answer(sql -> sql.contains("'uploads/**'"), FILE, List.of(
                    List.of("lake/uploads/ada/old/a.csv", 100L, "2026-09-01 08:00:00"),
                    List.of("lake/uploads/ada/old/b.csv", 50L, "2026-09-02 08:00:00"),
                    List.of("lake/uploads/ada/new/a.csv", 10L, "2026-10-20 08:00:00"),
                    List.of("lake/uploads/ada/read/readings.csv", 70L, "2026-08-01 08:00:00"),
                    List.of("lake/uploads/ada/loose.csv", 5L, "2026-01-01 08:00:00")))
            .answer(sql -> sql.contains("\"lakeViews\""), List.of("database", "tableCount", "lakeViews"), List.of(
                    List.of("default", 2L, 2L),
                    List.of("earthquakes", 9L, 9L),
                    List.of("hand_made", 3L, 2L),
                    List.of("old_sensors", 5L, 5L),
                    List.of("serving", 4L, 4L)));
    private final HousekeepingProperties settings = new HousekeepingProperties(7, 14, 30, 2, "-");
    private final Housekeeping housekeeping = housekeeping(specs);

    @TempDir
    Path specRoot;

    private Housekeeping housekeeping(SpecStore specs) {
        return new Housekeeping(serving, new LakeFunctions(Workspace.properties()),
                List.of(new OldReleases(releases, settings, new ObjectMapper()),
                        new RemovedCatalogs(new SystemTables(serving), serving, specs)),
                specs,
                new EngineTasks(null, specs, null, Workspace.properties(), null, null, null) {
                    @Override
                    public TaskStarted clean(List<String> keys, String person) {
                        cleaned.add(keys);
                        people.add(person);
                        return new TaskStarted("t-1", "r-1");
                    }
                }, settings, CLOCK);
    }

    @AfterEach
    void stop() {
        parsing.stop();
    }

    @Test
    void planListsOnlyWhatIsOldUnusedOrOrphaned() {
        release("examples", "c1", "c2", "c3", "c4");
        releases.put("examples/targets/prod.json", "{\"project\":\"examples\",\"commit\":\"c1\"}".getBytes(StandardCharsets.UTF_8));

        List<Candidate> plan = housekeeping.plan();

        assertThat(plan).extracting(Candidate::kind, Candidate::location, Candidate::files, Candidate::bytes).containsExactly(
                tuple(Kind.PREVIEWS, "system/tasks/", 1L, 1200L),
                tuple(Kind.EVENT_LOGS, "spark-events/", 2L, 8000L),
                tuple(Kind.CHECKPOINTS, "system/checkpoints/old_sensors/", 2L, 300L),
                tuple(Kind.UPLOADS, "uploads/", 3L, 220L),
                tuple(Kind.RELEASES, "examples/c2/", 2L, 2L),
                tuple(Kind.CATALOGS, "old_sensors", 5L, 0L));
        assertThat(plan.get(1).keys()).containsExactly("spark-events/eventlog_v2_app-1/");
        assertThat(plan.get(3).keys()).containsExactly("uploads/ada/old/", "uploads/ada/read/");
    }

    @Test
    void anUploadASpecReadsIsKeptHoweverOld() throws IOException {
        Path spec = Files.createDirectories(specRoot.resolve("readings")).resolve("pipeline.yml");
        Files.writeString(spec, "dataset: readings\nsource:\n  path: s3a://lake/uploads/ada/read/readings.csv\n");

        List<Candidate> plan =
                housekeeping(new SpecStore(Workspace.deployed(specRoot), new PipelineSpecLoader(), parsing)).plan();

        assertThat(plan).filteredOn(candidate -> candidate.kind() == Kind.UPLOADS)
                .singleElement()
                .satisfies(uploads -> assertThat(uploads.keys()).containsExactly("uploads/ada/old/"));
    }

    @Test
    void applyRemovesReleasesAndCatalogsHereAndSendsLakeLocationsToOneEngineTask() {
        release("examples", "c1", "c2", "c3");

        Housekeeping.Applied applied =
                housekeeping.apply(Set.of(Kind.PREVIEWS, Kind.CHECKPOINTS, Kind.RELEASES, Kind.CATALOGS), "ada");

        assertThat(cleaned).containsExactly(List.of("system/tasks/old.json", "system/checkpoints/old_sensors/"));
        assertThat(people).containsExactly("ada");
        assertThat(applied.lakeLocations()).isEqualTo(2);
        assertThat(applied.removed()).isEqualTo(3);
        assertThat(serving.statements()).filteredOn(sql -> sql.startsWith("drop database"))
                .containsExactly("drop database if exists \"old_sensors\"");
        assertThat(releases.objects).doesNotContainKeys("examples/c1/release.json").containsKeys("examples/c3/release.json");
    }

    @Test
    void scheduledCleanUpRemovesEveryKindAsThePlatform() {
        release("examples", "c1", "c2", "c3");

        new ScheduledCleanup(housekeeping).clean();

        assertThat(cleaned).containsExactly(List.of(
                "system/tasks/old.json",
                "spark-events/eventlog_v2_app-1/",
                "system/checkpoints/old_sensors/",
                "uploads/ada/old/",
                "uploads/ada/read/"));
        assertThat(people).containsExactly("platform");
        assertThat(serving.statements()).filteredOn(sql -> sql.startsWith("drop database"))
                .containsExactly("drop database if exists \"old_sensors\"");
        assertThat(releases.objects).doesNotContainKeys("examples/c1/release.json");
    }

    private void release(String project, String... commits) {
        for (String commit : commits) {
            releases.put(project + "/" + commit + "/pipelines/x.yml", new byte[1]);
            releases.put(project + "/" + commit + "/release.json", new byte[1]);
        }
    }
}
