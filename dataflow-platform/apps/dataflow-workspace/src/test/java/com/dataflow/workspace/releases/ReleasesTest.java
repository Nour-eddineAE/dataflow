package com.dataflow.workspace.releases;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.ReleaseId;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.DraftAccess;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.folders.FolderViews.CreateFolder;
import com.dataflow.workspace.folders.GitFixture;
import com.dataflow.workspace.folders.GitFolderException;
import com.dataflow.workspace.folders.JavaChecks;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.specs.SpecStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dataflow.workspace.folders.GitFixture.ADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ReleasesTest {

    private final List<Object> published = new ArrayList<>();
    private final ObjectMapper json = new ObjectMapper();
    private final InMemoryReleaseStore store = new InMemoryReleaseStore();
    private final List<ReleaseRecord> history = new ArrayList<>();

    @TempDir
    Path temp;

    private int reloads;

    private GitFixture fixture;
    private DeployedSpecs deployed;
    private final DraftAccess drafts = mock(DraftAccess.class);
    private Releases releases;
    private UUID folder;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new GitFixture(temp);
        WorkspaceProperties base = Workspace.properties();
        WorkspaceProperties properties = new WorkspaceProperties(
                base.person(),
                base.auth(),
                base.git(),
                new WorkspaceProperties.Releases(
                        "releases",
                        "",
                        "",
                        "",
                        "prod",
                        temp.resolve("releases").toString(),
                        base.releases().refreshEvery(),
                        base.releases().region(),
                        base.releases().pathStyle()),
                base.serving(),
                base.tower(),
                base.catalog(),
                base.links(),
                base.alerts(),
                base.dashboards());
        deployed = new DeployedSpecs(store, json, properties);
        Tower tower = new Tower(Workspace.properties(), HttpClient.newHttpClient(), json) {
            @Override
            public boolean reload() {
                reloads++;
                return true;
            }
        };
        ReleaseLog log = new ReleaseLog() {
            @Override
            public void record(ReleaseRecord release) {
                history.add(release);
            }

            @Override
            public List<ReleaseRecord> history(String project, int limit) {
                return history;
            }
        };
        releases = new Releases(
                fixture.sync,
                new ProjectSnapshots(new JavaChecks(), new PipelineSpecLoader(), fixture.parsing),
                new ReleaseWriter(store, log, json, Clock.fixed(Instant.parse("2026-10-25T18:30:00Z"), ZoneOffset.UTC)),
                deployed,
                tower,
                published::add,
                drafts);
        folder = fixture.folders
                .create(new CreateFolder("examples", fixture.remoteUrl(), "main", true, null), ADA)
                .id();
    }

    @AfterEach
    void stop() {
        fixture.close();
    }

    @Test
    void release_writesTheCommitThenThePointerAndReloadsTheTower() {
        Releases.ReleaseView view = releases.release(folder, "prod", ADA);

        ReleaseId id = new ReleaseId("examples", view.commit());
        assertThat(view.datasets()).containsExactly("earthquakes");
        assertThat(store.objects)
                .containsKeys(
                        ReleaseFiles.file(id, "dataflow.yml"),
                        ReleaseFiles.file(id, "pipelines/earthquakes/pipeline.yml"),
                        ReleaseFiles.manifest(id),
                        ReleaseFiles.pointer("examples", "prod"));
        assertThat(store.objects).doesNotContainKey(ReleaseFiles.file(id, "README.md"));
        assertThat(new String(store.objects.get(ReleaseFiles.pointer("examples", "prod")), StandardCharsets.UTF_8))
                .contains(view.commit())
                .contains("\"releasedBy\" : \"ada\"");
        assertThat(deployed.releaseOf("earthquakes")).hasValue(id);
        assertThat(new SpecStore(deployed, new PipelineSpecLoader(), fixture.parsing)
                        .spec("earthquakes")
                        .owner())
                .isEqualTo("platform");
        assertThat(reloads).isEqualTo(1);
        assertThat(history)
                .singleElement()
                .satisfies(record -> assertThat(record.folderId()).isEqualTo(folder));
        assertThat(published).containsExactly(new Released("examples", "prod", "ada", Map.of("earthquakes", "platform")));
    }

    @Test
    void release_toProductionTakesTheRemoteBranchNotUnpushedWork() throws Exception {
        fixture.files.save(
                folder,
                "pipelines/earthquakes/pipeline.yml",
                fixture.remoteFile("pipelines/earthquakes/pipeline.yml").replace("owner: platform", "owner: geo"),
                ADA);
        fixture.sync.commit(folder, "feat: local only", ADA);

        releases.release(folder, "prod", ADA);

        assertThat(new SpecStore(deployed, new PipelineSpecLoader(), fixture.parsing)
                        .spec("earthquakes")
                        .owner())
                .isEqualTo("platform");
    }

    @Test
    void release_refusesADatasetAnotherProjectReleased() throws Exception {
        releases.release(folder, "prod", ADA);
        fixture.commitOnRemote(
                "dataflow.yml",
                GitFixture.PROJECT.replace("project: examples", "project: copycat"),
                "chore: rename the project");
        fixture.sync.pull(folder);

        assertThatThrownBy(() -> releases.release(folder, "prod", ADA))
                .isInstanceOf(GitFolderException.class)
                .hasMessageContaining("Dataset earthquakes is already released by project examples");
    }

    @Test
    void release_refusesAStepWhoseTransformationIsMissing() throws Exception {
        String spec = fixture.remoteFile("pipelines/earthquakes/pipeline.yml")
                .replace("  derive:", "  steps:\n    - { name: echoed, transformation: echo@1 }\n  derive:");
        fixture.commitOnRemote("pipelines/earthquakes/pipeline.yml", spec, "feat: an echo step");
        fixture.sync.pull(folder);

        assertThatThrownBy(() -> releases.release(folder, "prod", ADA))
                .isInstanceOf(GitFolderException.class)
                .hasMessageContaining("earthquakes step echoed uses echo@1, which no transformation of");

        fixture.commitOnRemote("transformations/Echo.java", GitFixture.ECHO, "feat: echo");
        fixture.sync.pull(folder);
        assertThat(releases.release(folder, "prod", ADA).datasets()).containsExactly("earthquakes");
    }

    @Test
    void release_refusesAPipelineThatReadsWhatTheCallerMayNot() {
        doThrow(new AccessDeniedException("bob lacks SELECT on table:taxi_trips.current"))
                .when(drafts)
                .require(any(PipelineSpec.class), any());

        assertThatThrownBy(() -> releases.release(folder, "prod", ADA)).isInstanceOf(AccessDeniedException.class);
        assertThat(store.objects).isEmpty();
    }

    @Test
    void release_needsAKnownTarget() {
        assertThatThrownBy(() -> releases.release(folder, "staging", ADA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no target 'staging'");
    }
}
