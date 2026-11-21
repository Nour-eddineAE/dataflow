package com.dataflow.workspace.folders;

import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

final class Clone implements AutoCloseable {

    private final Git git;
    private final CredentialsProvider credentials;

    Clone(Git git, CredentialsProvider credentials) {
        this.git = git;
        this.credentials = credentials;
    }

    Git git() {
        return git;
    }

    Repository repository() {
        return git.getRepository();
    }

    Optional<RevCommit> head() throws IOException {
        ObjectId head = repository().resolve(Constants.HEAD);
        if (head == null) {
            return Optional.empty();
        }
        try (RevWalk walk = new RevWalk(repository())) {
            return Optional.of(walk.parseCommit(head));
        }
    }

    Optional<String> file(RevCommit commit, String path) {
        try (TreeWalk walk = TreeWalk.forPath(repository(), path, commit.getTree())) {
            return walk == null ? Optional.empty() : Optional.of(read(walk.getObjectId(0)));
        } catch (IOException e) {
            throw new GitFolderException("Cannot read " + path + ": " + e.getMessage(), e);
        }
    }

    Optional<String> file(String path) throws IOException {
        return head().flatMap(commit -> file(commit, path));
    }

    List<String> paths(RevCommit commit) throws IOException {
        List<String> paths = new ArrayList<>();
        try (TreeWalk tree = walk(commit)) {
            while (tree.next()) {
                paths.add(tree.getPathString());
            }
        }
        return paths;
    }

    Map<String, byte[]> files(RevCommit commit, Predicate<String> include) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (TreeWalk tree = walk(commit)) {
            while (tree.next()) {
                if (include.test(tree.getPathString())) {
                    files.put(tree.getPathString(), repository().open(tree.getObjectId(0)).getBytes());
                }
            }
        }
        return files;
    }

    int unpushed(RevCommit head) throws IOException {
        try (RevWalk walk = new RevWalk(repository())) {
            walk.markStart(walk.parseCommit(head));
            for (Ref remote : repository().getRefDatabase().getRefsByPrefix(Branches.REMOTE)) {
                walk.markUninteresting(walk.parseCommit(remote.getObjectId()));
            }
            int count = 0;
            for (RevCommit ignored : walk) {
                count++;
            }
            return count;
        }
    }

    List<String> branches() throws GitAPIException {
        TreeSet<String> names = new TreeSet<>();
        for (Ref ref : git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()) {
            String name = ref.getName();
            if (name.startsWith(Constants.R_HEADS)) {
                names.add(name.substring(Constants.R_HEADS.length()));
            } else if (name.startsWith(Branches.REMOTE) && !name.endsWith("/" + Constants.HEAD)) {
                names.add(name.substring(Branches.REMOTE.length()));
            }
        }
        return List.copyOf(names);
    }

    void checkout(String branch) throws GitAPIException, IOException {
        Ref local = repository().findRef(Constants.R_HEADS + branch);
        Ref remote = repository().findRef(Branches.remote(branch));
        boolean born = repository().resolve(Constants.HEAD) != null;
        if (born) {
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
        }
        if (local != null) {
            git.checkout().setName(branch).call();
        } else if (remote != null) {
            git.checkout()
                    .setCreateBranch(true)
                    .setName(branch)
                    .setStartPoint(remote.getName())
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .call();
        } else if (born) {
            git.checkout().setCreateBranch(true).setName(branch).call();
        } else {
            RefUpdate head = repository().updateRef(Constants.HEAD);
            head.disableRefLog();
            head.link(Constants.R_HEADS + branch);
        }
    }

    void fetch() throws GitAPIException {
        git.fetch().setRemote(Branches.ORIGIN).setCredentialsProvider(credentials).call();
    }

    Iterable<PushResult> push(String branch) throws GitAPIException {
        String ref = Constants.R_HEADS + branch;
        return git.push()
                .setRemote(Branches.ORIGIN)
                .setRefSpecs(new RefSpec(ref + ":" + ref))
                .setCredentialsProvider(credentials)
                .call();
    }

    void track(String branch) throws IOException {
        StoredConfig config = repository().getConfig();
        if (config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE) == null) {
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE, Branches.ORIGIN);
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branch);
            config.save();
        }
    }

    @Override
    public void close() {
        git.close();
    }

    private TreeWalk walk(RevCommit commit) throws IOException {
        TreeWalk tree = new TreeWalk(repository());
        tree.addTree(commit.getTree());
        tree.setRecursive(true);
        return tree;
    }

    private String read(ObjectId blob) throws IOException {
        return new String(repository().open(blob).getBytes(), StandardCharsets.UTF_8);
    }
}
