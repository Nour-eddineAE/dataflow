package com.dataflow.workspace.folders;

import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.specs.Parsing;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jasypt.encryption.StringEncryptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class GitFixture implements AutoCloseable {

    public static final Person ADA = new Person("ada", "Ada Lovelace", "ada@dataflow.local");
    public static final String PROJECT = "project: examples\n"
            + "targets:\n"
            + "  dev:\n"
            + "    mode: development\n"
            + "  prod:\n"
            + "    mode: production\n"
            + "    branch: main\n";

    public static final String ECHO = """
        import com.dataflow.transformation.DataTransformation;
        import com.dataflow.transformation.NoParams;
        import com.dataflow.transformation.Transformation;
        import org.apache.spark.sql.Dataset;
        import org.apache.spark.sql.Row;

        @DataTransformation(name = "echo")
        public final class Echo implements Transformation<Echo.In, NoParams> {
            public record In(Dataset<Row> rows) {}

            public Dataset<Row> apply(In in, NoParams params) {
                return in.rows();
            }
        }
        """;

    public final Path remote;
    public final InMemoryFolderStore store = new InMemoryFolderStore();
    public final Parsing parsing = new Parsing();
    public final GitFolders folders;
    public final FolderFiles files;
    public final FolderSync sync;
    public final GitCredentials credentials;

    public GitFixture(Path temp) throws Exception {
        this.remote = temp.resolve("remotes").resolve("examples");
        Files.createDirectories(remote.resolve("pipelines/earthquakes"));
        Files.writeString(remote.resolve("dataflow.yml"), PROJECT);
        Files.copy(
                Workspace.PIPELINES.resolve("earthquakes/pipeline.yml"),
                remote.resolve("pipelines/earthquakes/pipeline.yml"));
        Files.writeString(remote.resolve("README.md"), "# Examples\n");
        try (Git git = Git.init()
                .setInitialBranch("main")
                .setDirectory(remote.toFile())
                .call()) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setMessage("feat: first pipelines")
                    .setAuthor(author())
                    .setCommitter(author())
                    .call();
        }
        WorkspaceProperties properties = Workspace.properties(temp.resolve("cache"));
        WorkspaceProperties withRemotes = new WorkspaceProperties(
                properties.person(),
                properties.auth(),
                new WorkspaceProperties.Git(
                        properties.git().cacheDir(),
                        List.of(temp.resolve("remotes").toString()),
                        properties.git().github()),
                properties.releases(),
                properties.serving(),
                properties.tower(),
                properties.catalog(),
                properties.links(),
                properties.alerts(),
                properties.dashboards());
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T18:00:00Z"), ZoneOffset.UTC);
        this.credentials = new GitCredentials(
                store,
                new PlainEncryptor(),
                new GitHub(
                        withRemotes,
                        HttpClient.newHttpClient(),
                        new ObjectMapper(),
                        new InMemoryGitHubAppStore(),
                        new PlainEncryptor()),
                clock);
        GitCache cache = new GitCache(withRemotes, credentials);
        FolderProjects projects = new FolderProjects(new PipelineSpecLoader(), parsing);
        this.folders = new GitFolders(store, cache, projects, withRemotes, clock);
        this.files = new FolderFiles(
                folders,
                store,
                cache,
                new FileChecks(new PipelineSpecLoader(), parsing),
                new JavaChecks(),
                projects,
                clock);
        this.sync = new FolderSync(folders, files, store, cache, projects, clock);
    }

    private static PersonIdent author() {
        return new PersonIdent("Remote", "remote@dataflow.local");
    }

    public String remoteUrl() {
        return remote.toUri().toString();
    }

    public void commitOnRemote(String path, String content, String message) throws Exception {
        Path file = remote.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        try (Git git = Git.open(remote.toFile())) {
            git.add().addFilepattern(path).call();
            git.commit()
                    .setMessage(message)
                    .setAuthor(author())
                    .setCommitter(author())
                    .call();
        }
    }

    public String remoteFile(String path) throws IOException {
        try (Git git = Git.open(remote.toFile());
                RevWalk walk = new RevWalk(git.getRepository())) {
            RevCommit main = walk.parseCommit(git.getRepository().resolve("refs/heads/main"));
            try (TreeWalk tree = TreeWalk.forPath(git.getRepository(), path, main.getTree())) {
                if (tree == null) {
                    throw new IOException("No " + path + " on the remote's main");
                }
                return new String(git.getRepository().open(tree.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    @Override
    public void close() {
        parsing.stop();
    }

    static final class PlainEncryptor implements StringEncryptor {

        @Override
        public String encrypt(String message) {
            return "enc:" + message;
        }

        @Override
        public String decrypt(String encryptedMessage) {
            return encryptedMessage.substring("enc:".length());
        }
    }
}
