package com.dataflow.workspace.folders;

import com.dataflow.workspace.config.WorkspaceProperties;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class GitCache {

    private final Path root;
    private final GitCredentials credentials;
    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public GitCache(WorkspaceProperties properties, GitCredentials credentials) {
        this.root = Path.of(properties.git().cacheDir()).toAbsolutePath().normalize();
        this.credentials = credentials;
    }

    private static void delete(Path dir) {
        try {
            if (Files.exists(dir)) {
                FileUtils.delete(dir.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
            }
        } catch (IOException e) {
            throw new GitFolderException("Cannot clear the cache " + dir + ": " + e.getMessage(), e);
        }
    }

    <T> T with(GitFolder folder, GitWork<T> work) {
        CredentialsProvider provider = credentials.provider(folder);
        ReentrantLock lock = locks.computeIfAbsent(folder.id(), id -> new ReentrantLock());
        lock.lock();
        try (Clone clone = open(folder, provider)) {
            return work.apply(clone);
        } catch (GitAPIException | IOException e) {
            throw new GitFolderException(folder.path() + ": " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public void forget(UUID id) {
        ReentrantLock lock = locks.computeIfAbsent(id, key -> new ReentrantLock());
        lock.lock();
        try {
            delete(directory(id));
        } finally {
            lock.unlock();
            locks.remove(id);
        }
    }

    Path directory(UUID id) {
        return root.resolve(id.toString());
    }

    private Clone open(GitFolder folder, CredentialsProvider provider) throws GitAPIException, IOException {
        Path dir = directory(folder.id());
        if (!Files.isDirectory(dir.resolve(Constants.DOT_GIT))) {
            delete(dir);
            Files.createDirectories(dir);
            log.info("Cloning {} into the cache of folder {}", folder.remoteUrl(), folder.path());
            Git.cloneRepository()
                    .setURI(folder.remoteUrl())
                    .setDirectory(dir.toFile())
                    .setBranch(folder.branch())
                    .setCredentialsProvider(provider)
                    .call()
                    .close();
        }
        Clone clone = new Clone(Git.open(dir.toFile()), provider);
        if (!folder.branch().equals(clone.repository().getBranch())) {
            clone.checkout(folder.branch());
        }
        return clone;
    }

    @FunctionalInterface
    interface GitWork<T> {
        T apply(Clone clone) throws GitAPIException, IOException;
    }
}
