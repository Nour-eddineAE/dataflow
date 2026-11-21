package com.dataflow.workspace.folders;

import com.dataflow.workspace.folders.FolderViews.CommitView;
import com.dataflow.workspace.folders.FolderViews.FolderTree;
import com.dataflow.workspace.folders.FolderViews.Snapshot;
import com.dataflow.workspace.specs.Problem;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

@Service
public class FolderSync {

    private static final int LOG_LENGTH = 30;

    private final GitFolders folders;
    private final FolderFiles files;
    private final FolderStore store;
    private final GitCache cache;
    private final FolderProjects projects;
    private final Clock clock;

    public FolderSync(
            GitFolders folders,
            FolderFiles files,
            FolderStore store,
            GitCache cache,
            FolderProjects projects,
            Clock clock) {
        this.folders = folders;
        this.files = files;
        this.store = store;
        this.cache = cache;
        this.projects = projects;
        this.clock = clock;
    }

    private static CommitView view(RevCommit commit) {
        return new CommitView(
                commit.name(),
                commit.getShortMessage(),
                commit.getAuthorIdent().getName(),
                commit.getAuthorIdent().getWhenAsInstant());
    }

    private static void checkPushed(String branch, Iterable<PushResult> results) {
        for (PushResult result : results) {
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                RemoteRefUpdate.Status status = update.getStatus();
                if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                    throw new GitFolderException("Push of " + branch + " refused (" + status + ")"
                            + (status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD ? ": pull first" : "")
                            + (update.getMessage() == null ? "" : ": " + update.getMessage()));
                }
            }
        }
    }

    private static void write(Git git, List<Draft> drafts) throws GitAPIException, IOException {
        Path workTree = git.getRepository().getWorkTree().toPath();
        for (Draft draft : drafts) {
            Path file = workTree.resolve(draft.path());
            if (draft.deleted()) {
                Files.deleteIfExists(file);
                git.rm().addFilepattern(draft.path()).call();
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, draft.content());
                git.add().addFilepattern(draft.path()).call();
            }
        }
    }

    private static void merge(Git git, Ref upstream, String branch) throws GitAPIException, IOException {
        ObjectId before = git.getRepository().resolve(Constants.HEAD);
        MergeResult merge = git.merge()
                .include(upstream)
                .setFastForward(MergeCommand.FastForwardMode.FF)
                .setMessage("Merge " + Branches.ORIGIN + "/" + branch)
                .call();
        if (!merge.getMergeStatus().isSuccessful()) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(before.name()).call();
            throw new GitFolderException("Pulling " + branch + " conflicts with local commits ("
                    + merge.getMergeStatus() + "); resolve it in a clone and push");
        }
    }

    public List<String> branches(UUID id) {
        return cache.with(folders.folder(id), Clone::branches);
    }

    public FolderTree checkout(UUID id, String branch) {
        GitFolder folder = folders.folder(id);
        GitFolder switched = folder.withBranch(Branches.check(branch));
        cache.with(folder, clone -> {
            clone.checkout(branch);
            return null;
        });
        store.updateBranch(id, branch);
        return cache.with(switched, clone -> files.tree(switched, clone));
    }

    public FolderTree pull(UUID id) {
        GitFolder folder = folders.folder(id);
        return cache.with(folder, clone -> {
            clone.fetch();
            Ref upstream = clone.repository().findRef(Branches.remote(folder.branch()));
            if (upstream != null && clone.repository().resolve(Constants.HEAD) == null) {
                clone.checkout(folder.branch());
            } else if (upstream != null) {
                merge(clone.git(), upstream, folder.branch());
            }
            return files.tree(folder, clone);
        });
    }

    public CommitView commit(UUID id, String message, Person person) {
        GitFolder folder = folders.folder(id);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A commit needs a message");
        }
        List<Draft> drafts = store.drafts(folder.id(), folder.branch());
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("Nothing to commit in " + folder.path());
        }
        CommitView view = commit(folder, drafts, message, person);
        store.deleteDrafts(folder.id(), folder.branch(), drafts.stream().map(Draft::path).toList());
        return view;
    }

    public CommitView commitFile(UUID id, String path, String content, String message, Person person) {
        GitFolder folder = folders.folder(id);
        String file = FilePaths.check(path);
        if (store.drafts(folder.id(), folder.branch()).stream().anyMatch(draft -> draft.path().equals(file))) {
            throw new GitFolderException(file + " has unsaved changes in " + folder.path() + ": commit or discard them first");
        }
        Draft change = new Draft(folder.id(), folder.branch(), file, content, null, person.id(), clock.instant());
        return commit(folder, List.of(change), message, person);
    }

    private CommitView commit(GitFolder folder, List<Draft> drafts, String message, Person person) {
        List<Problem> problems = files.problems(folder, drafts);
        if (!problems.isEmpty()) {
            throw new GitFolderException("Fix the problems before committing", problems, null);
        }
        return cache.with(folder, clone -> {
            Git git = clone.git();
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            write(git, drafts);
            try {
                projects.check(git.getRepository().getWorkTree().toPath());
            } catch (GitFolderException e) {
                git.reset().setMode(ResetCommand.ResetType.HARD).call();
                throw e;
            }
            PersonIdent ident = new PersonIdent(person.name(), person.email(), Instant.now(clock), ZoneOffset.UTC);
            return view(git.commit()
                    .setMessage(message.strip())
                    .setAuthor(ident)
                    .setCommitter(ident)
                    .call());
        });
    }

    public FolderTree push(UUID id) {
        GitFolder folder = folders.folder(id);
        return cache.with(folder, clone -> {
            checkPushed(folder.branch(), clone.push(folder.branch()));
            clone.track(folder.branch());
            clone.fetch();
            return files.tree(folder, clone);
        });
    }

    public List<CommitView> log(UUID id) {
        return cache.with(folders.folder(id), clone -> {
            List<CommitView> commits = new ArrayList<>();
            for (RevCommit commit : clone.git().log().setMaxCount(LOG_LENGTH).call()) {
                commits.add(view(commit));
            }
            return commits;
        });
    }

    public Snapshot snapshot(UUID id, String ref, Predicate<String> include) {
        GitFolder folder = folders.folder(id);
        return cache.with(folder, clone -> {
            clone.fetch();
            ObjectId target = clone.repository().resolve(ref);
            if (target == null) {
                throw new GitFolderException(folder.path() + " has no " + ref);
            }
            try (RevWalk walk = new RevWalk(clone.repository())) {
                RevCommit commit = walk.parseCommit(target);
                return new Snapshot(folder, commit.name(), clone.files(commit, include));
            }
        });
    }
}
