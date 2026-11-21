package com.dataflow.workspace.folders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryGitHubAppStore implements GitHubAppStore {

    private final Map<String, GitHubApp> apps = new LinkedHashMap<>();

    @Override
    public Optional<GitHubApp> find(String host) {
        return Optional.ofNullable(apps.get(host));
    }

    @Override
    public void save(GitHubApp app) {
        apps.put(app.host(), app);
    }
}
