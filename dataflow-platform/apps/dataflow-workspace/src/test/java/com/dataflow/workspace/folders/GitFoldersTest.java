package com.dataflow.workspace.folders;

import com.dataflow.workspace.folders.FolderViews.CreateFolder;
import com.dataflow.workspace.folders.FolderViews.FileContent;
import com.dataflow.workspace.folders.FolderViews.FileState;
import com.dataflow.workspace.folders.FolderViews.FolderFile;
import com.dataflow.workspace.folders.FolderViews.FolderTree;
import java.time.Instant;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static com.dataflow.workspace.folders.GitFixture.ADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitFoldersTest {

    private static final String SPEC = "pipelines/earthquakes/pipeline.yml";

    @TempDir
    Path temp;

    private GitFixture fixture;
    private GitFolders folders;
    private FolderFiles files;
    private FolderSync sync;
    private UUID id;

    @BeforeEach
    void connect() throws Exception {
        fixture = new GitFixture(temp);
        folders = fixture.folders;
        files = fixture.files;
        sync = fixture.sync;
        id = folders.create(new CreateFolder("examples", fixture.remoteUrl(), "main", false, null), ADA)
                .id();
    }

    @AfterEach
    void stop() {
        fixture.close();
    }

    @Test
    void create_refusesACredentialOfAnotherPerson() {
        UUID theirs = UUID.randomUUID();
        fixture.store.insertCredential(
                new GitCredential(theirs, "someone-else", "Their token", "them", null, Instant.EPOCH),
                StoredSecret.of("enc:secret"));

        assertThatThrownBy(() -> folders.create(
                        new CreateFolder("theirs", fixture.remoteUrl(), "main", false, theirs), ADA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_clonesTheRemoteIntoAPersonalFolder() {
        FolderTree tree = files.tree(id);

        assertThat(tree.path()).isEqualTo("Users/ada/examples");
        assertThat(tree.project()).isEqualTo("examples");
        assertThat(tree.branch()).isEqualTo("main");
        assertThat(tree.files()).extracting(FolderFile::path).containsExactly("README.md", "dataflow.yml", SPEC);
        assertThat(tree.files()).extracting(FolderFile::state).containsOnly(FileState.UNCHANGED);
        assertThat(folders.list())
                .singleElement()
                .satisfies(summary -> assertThat(summary.project()).isEqualTo("examples"));
    }

    @Test
    void create_refusesRemotesThatAreNotHttpsOrAllowedFolders() {
        assertThatThrownBy(() ->
                        folders.create(new CreateFolder("x", "http://git.example.com/x.git", "main", false, null), ADA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https://");
        assertThatThrownBy(() -> folders.create(new CreateFolder("y", "file:///etc", "main", false, null), ADA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Local remotes are allowed only under");
    }

    @Test
    void save_keepsADraftAndReportsItsProblems() throws Exception {
        String broken = fixture.remoteFile(SPEC).replace("version: 3", "version: 1");

        FileContent saved = files.save(id, SPEC, broken, ADA);

        assertThat(saved.state()).isEqualTo(FileState.MODIFIED);
        assertThat(saved.problems())
                .extracting(problem -> problem.message())
                .anyMatch(m -> m.contains("version must be 3"));
        assertThat(saved.committed()).contains("version: 3");
        assertThatThrownBy(() -> sync.commit(id, "break it", ADA))
                .isInstanceOf(GitFolderException.class)
                .satisfies(e -> assertThat(((GitFolderException) e).problems())
                        .anyMatch(problem -> problem.message().startsWith(SPEC + ": version must be 3")));
    }

    @Test
    void commitAndPush_writeTheDraftsAsThePersonAndUpdateTheRemote() throws Exception {
        String changed = fixture.remoteFile(SPEC).replace("owner: platform", "owner: geo");
        files.save(id, SPEC, changed, ADA);
        files.save(id, "notes/todo.md", "- check depths\n", ADA);

        FolderViews.CommitView commit = sync.commit(id, "feat: hand earthquakes to geo", ADA);
        FolderTree pushed = sync.push(id);

        assertThat(commit.author()).isEqualTo("Ada Lovelace");
        assertThat(pushed.ahead()).isZero();
        assertThat(pushed.files()).extracting(FolderFile::state).containsOnly(FileState.UNCHANGED);
        assertThat(fixture.remoteFile(SPEC)).contains("owner: geo");
        assertThat(fixture.remoteFile("notes/todo.md")).isEqualTo("- check depths\n");
        assertThat(sync.log(id))
                .first()
                .satisfies(top -> assertThat(top.message()).isEqualTo("feat: hand earthquakes to geo"));
    }

    @Test
    void anEmptyRemote_startsAsAProjectAndItsFirstPushCreatesTheBranch() throws Exception {
        Path empty = temp.resolve("remotes").resolve("super-duper-carnival.git");
        Git.init()
                .setBare(true)
                .setInitialBranch("master")
                .setDirectory(empty.toFile())
                .call()
                .close();

        UUID created = folders.create(
                        new CreateFolder("super-duper-carnival", empty.toUri().toString(), "main", true, null), ADA)
                .id();
        FolderTree started = files.tree(created);

        assertThat(started.head()).isNull();
        assertThat(started.project()).isEqualTo("super-duper-carnival");
        assertThat(started.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("dataflow.yml");
            assertThat(file.state()).isEqualTo(FileState.ADDED);
        });

        sync.commit(created, "chore: start the project", ADA);
        assertThat(files.tree(created).ahead()).isEqualTo(1);
        FolderTree pushed = sync.push(created);

        assertThat(pushed.ahead()).isZero();
        try (Git remote = Git.open(empty.toFile())) {
            assertThat(remote.getRepository().resolve("refs/heads/main")).isNotNull();
        }
    }

    @Test
    void save_checksADashboardFile() {
        FileContent saved = files.save(id, "dashboards/earthquakes.yml", "hide: [time]\npin: [current/time]\n", ADA);

        assertThat(saved.problems())
                .extracting(problem -> problem.message())
                .anyMatch(m -> m.contains("unknown key 'pin'"));
        assertThat(files.save(id, "dashboards/earthquakes.yml", "hide: [current/histogram-depthKm]\n", ADA)
                        .problems())
                .isEmpty();
    }

    @Test
    void pull_bringsRemoteCommits() throws Exception {
        fixture.commitOnRemote("README.md", "# Examples\n\nUpdated.\n", "docs: update readme");

        FolderTree tree = sync.pull(id);

        assertThat(tree.behind()).isZero();
        assertThat(files.read(id, "README.md").content()).contains("Updated.");
    }

    @Test
    void checkout_switchesToANewBranchWithItsOwnDrafts() {
        files.save(id, "notes/a.md", "a\n", ADA);

        FolderTree branch = sync.checkout(id, "zone-names");

        assertThat(branch.branch()).isEqualTo("zone-names");
        assertThat(branch.files()).extracting(FolderFile::path).doesNotContain("notes/a.md");
        assertThat(sync.branches(id)).contains("main", "zone-names");
    }

    @Test
    void save_compilesATransformationAndPointsAtTheFaultyLine() {
        FileContent broken =
                files.save(id, "transformations/Echo.java", GitFixture.ECHO.replace("in.rows()", "in.row()"), ADA);
        FileContent fine = files.save(id, "transformations/Echo.java", GitFixture.ECHO, ADA);

        assertThat(broken.problems()).anySatisfy(problem -> {
            assertThat(problem.message()).contains("row()");
            assertThat(problem.line()).isEqualTo(12);
        });
        assertThat(fine.problems()).isEmpty();
        assertThat(files.transformations(id)).containsOnlyKeys("transformations/Echo.java");
    }

    @Test
    void paths_cannotLeaveTheFolder() {
        assertThatThrownBy(() -> files.save(id, "../outside.txt", "x", ADA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> files.save(id, ".git/config", "x", ADA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
