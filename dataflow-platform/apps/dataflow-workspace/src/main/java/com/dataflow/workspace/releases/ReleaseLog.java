package com.dataflow.workspace.releases;

import java.util.List;

public interface ReleaseLog {

    void record(ReleaseRecord release);

    List<ReleaseRecord> history(String project, int limit);
}
