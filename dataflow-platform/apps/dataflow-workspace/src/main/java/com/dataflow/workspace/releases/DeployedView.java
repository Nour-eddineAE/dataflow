package com.dataflow.workspace.releases;

import com.dataflow.spec.ReleaseId;

import java.nio.file.Path;
import java.util.Optional;

public interface DeployedView {

    Path root();

    Optional<ReleaseId> releaseOf(String dataset);

    default Optional<String> releasedFile(String dataset, String path) {
        return Optional.empty();
    }
}
