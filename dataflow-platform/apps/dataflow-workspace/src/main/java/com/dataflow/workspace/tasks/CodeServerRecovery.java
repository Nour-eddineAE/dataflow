package com.dataflow.workspace.tasks;

import com.dataflow.spec.Names;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.runs.TowerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class CodeServerRecovery {

    private static final int RELOADS = 2;

    private final Tower tower;
    private final String job;
    private final Map<String, Object> probe;

    public CodeServerRecovery(Tower tower, WorkspaceProperties properties) {
        this.tower = tower;
        this.job = properties.tower().taskJob();
        this.probe = Map.of("ops", Map.of(EngineTasks.OP, Map.of("config", Map.of(
                "command", TaskKind.PREVIEW.command(),
                "dataset", Names.PLATFORM,
                "task_id", "code-server-check"))));
    }

    @Scheduled(fixedDelayString = WorkspaceProperties.SYNC_EVERY,
            initialDelayString = WorkspaceProperties.SYNC_EVERY)
    public void check() {
        try {
            if (tower.reachesCode(job, probe)) {
                return;
            }
            log.warn("The tower's code server is unreachable; reloading its code location");
            for (int reload = 1; reload <= RELOADS; reload++) {
                if (tower.reload() && tower.reachesCode(job, probe)) {
                    log.info("The tower's code server answers again after reload {} of {}", reload, RELOADS);
                    return;
                }
            }
            log.error("The tower's code server is still unreachable after {} reloads; recreate tower-code", RELOADS);
        } catch (TowerException e) {
            log.warn("The tower's code server was not checked: {}", e.getMessage());
        }
    }
}
