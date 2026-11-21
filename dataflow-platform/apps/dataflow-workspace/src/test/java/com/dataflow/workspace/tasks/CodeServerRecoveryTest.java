package com.dataflow.workspace.tasks;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.runs.TowerException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CodeServerRecoveryTest {

    private final Deque<Boolean> answers = new ArrayDeque<>();
    private final Deque<Boolean> loads = new ArrayDeque<>();
    private final ScriptedTower tower = new ScriptedTower();
    private final CodeServerRecovery recovery = new CodeServerRecovery(tower, Workspace.properties());

    @Test
    void aHealthyCodeServerIsLeftAlone() {
        answers.add(true);

        recovery.check();

        assertThat(tower.reloads).isZero();
        assertThat(tower.job).isEqualTo("engine_task");
        assertThat(tower.runConfig).isEqualTo(Map.of("ops", Map.of("run_engine_task", Map.of("config", Map.of(
                "command", "preview", "dataset", "platform", "task_id", "code-server-check")))));
    }

    @Test
    void aDeadCodeServerAnswersAgainAfterTheSecondReload() {
        answers.addAll(List.of(false, true));
        loads.addAll(List.of(false, true));

        recovery.check();

        assertThat(tower.reloads).isEqualTo(2);
        assertThat(answers).isEmpty();
    }

    @Test
    void aCodeServerThatStaysDeadIsReloadedOnlyTwice() {
        answers.addAll(List.of(false, false, false));
        loads.addAll(List.of(true, true));

        recovery.check();

        assertThat(tower.reloads).isEqualTo(2);
        assertThat(answers).isEmpty();
    }

    @Test
    void anUnreachableTowerIsLoggedWithoutReloading() {
        assertThatCode(recovery::check).doesNotThrowAnyException();

        assertThat(tower.reloads).isZero();
    }

    private final class ScriptedTower extends Tower {

        private int reloads;
        private String job;
        private Map<String, Object> runConfig;

        ScriptedTower() {
            super(Workspace.properties(), HttpClient.newHttpClient(), JsonMapper.builder().build());
        }

        @Override
        public boolean reachesCode(String job, Map<String, Object> runConfig) {
            this.job = job;
            this.runConfig = runConfig;
            Boolean answer = answers.poll();
            if (answer == null) {
                throw new TowerException("The tower (Dagster) is not reachable");
            }
            return answer;
        }

        @Override
        public boolean reload() {
            reloads++;
            return loads.pop();
        }
    }
}
