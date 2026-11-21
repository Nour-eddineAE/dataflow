package com.dataflow.workspace.folders;

import java.util.Optional;

public interface GitHubAppStore {

    Optional<GitHubApp> find(String host);

    void save(GitHubApp app);
}
