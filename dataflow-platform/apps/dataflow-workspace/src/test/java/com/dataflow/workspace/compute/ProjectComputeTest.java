package com.dataflow.workspace.compute;

import com.dataflow.spec.ComputePolicy;
import com.dataflow.spec.ComputeSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.folders.FolderViews.CreateFolder;
import com.dataflow.workspace.folders.FolderViews.FileState;
import com.dataflow.workspace.folders.GitFixture;
import com.dataflow.workspace.folders.GitFolderException;
import com.dataflow.workspace.releases.DeployedSpecs;
import com.dataflow.workspace.releases.InMemoryReleaseStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static com.dataflow.workspace.folders.GitFixture.ADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectComputeTest {

    private static final String PROJECT_FILE = "dataflow.yml";
    private static final String README = "README.md";
    private static final ProjectCompute.Rights ALL = new ProjectCompute.Rights(true, true, true);
    private static final ProjectCompute.Rights NONE = new ProjectCompute.Rights(false, false, false);
    private static final ComputeSpec LARGER = new ComputeSpec(
            new ComputeSpec.Executors(1, 4), "1800m", null, 2, null, Map.of("spark.sql.shuffle.partitions", "32"));

    @TempDir
    Path temp;

    private GitFixture fixture;
    private ProjectCompute compute;
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
        DeployedSpecs deployed = new DeployedSpecs(new InMemoryReleaseStore(), new ObjectMapper(), properties);
        PipelineSpecLoader loader = new PipelineSpecLoader();
        compute = new ProjectCompute(
                fixture.folders,
                fixture.files,
                fixture.sync,
                deployed,
                loader,
                new ComputePolicy("2g", 2, null, null, 4));
        folder = fixture.folders
                .create(new CreateFolder("examples", fixture.remoteUrl(), "main", true, null), ADA)
                .id();
    }

    @AfterEach
    void stop() {
        fixture.close();
    }

    @Test
    void projects_showEveryTargetWithItsCompute() {
        assertThat(compute.projects(summary -> ALL))
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.project()).isEqualTo("examples");
                    assertThat(project.targets()).extracting(ProjectCompute.TargetView::name).containsExactly("dev", "prod");
                    assertThat(project.targets()).allSatisfy(target -> {
                        assertThat(target.compute()).isEqualTo(ComputeSpec.DEFAULT);
                        assertThat(target.releasedCommit()).isNull();
                    });
                });
        assertThat(compute.projects(summary -> NONE)).isEmpty();
    }

    @Test
    void update_commitsAndPushesOnlyTheProjectFile() throws Exception {
        fixture.files.save(folder, README, "# Examples, in progress\n", ADA);

        ProjectCompute.ProjectView view = compute.update(folder, "prod", LARGER, ADA, summary -> ALL);

        assertThat(view.targets().get(1).compute()).isEqualTo(LARGER);
        assertThat(view.head().message()).isEqualTo("chore(compute): size the prod runs");
        assertThat(view.head().author()).isEqualTo(ADA.name());
        assertThat(fixture.remoteFile(PROJECT_FILE))
                .contains("driver-memory: 1800m")
                .contains("spark.sql.shuffle.partitions: \"32\"");
        assertThat(fixture.remoteFile(README)).isEqualTo("# Examples\n");
        assertThat(fixture.files.read(folder, README).state()).isEqualTo(FileState.MODIFIED);
    }

    @Test
    void update_refusesAProjectFileWithUnsavedEditsAndABadSize() {
        assertThatThrownBy(() -> compute.update(folder, "prod", new ComputeSpec(null, "big", null, null, null, null), ADA, summary -> ALL))
                .hasMessageContaining("targets.prod.compute.driver-memory must be a positive size");

        fixture.files.save(folder, PROJECT_FILE, GitFixture.PROJECT.replace("examples", "samples"), ADA);

        assertThatThrownBy(() -> compute.update(folder, "prod", LARGER, ADA, summary -> ALL))
                .isInstanceOf(GitFolderException.class)
                .hasMessageContaining("dataflow.yml has unsaved changes");
    }

    @Test
    void update_refusesASizeAboveTheInstallPolicyBeforeCommitting() throws Exception {
        String before = fixture.remoteFile(PROJECT_FILE);

        assertThatThrownBy(() -> compute.update(folder, "prod",
                new ComputeSpec(new ComputeSpec.Executors(0, 6), "3g", null, 2, null, Map.of()), ADA, summary -> ALL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("compute.driver-memory 3g is above the install's limit of 2g; "
                        + "compute.executors.max 6 is above the install's limit of 4");
        assertThat(fixture.remoteFile(PROJECT_FILE)).isEqualTo(before);
        assertThat(compute.policy().maxDriverMemory()).isEqualTo("2g");
    }
}
