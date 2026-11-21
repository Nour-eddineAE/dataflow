package com.dataflow.workspace.tasks;

import com.dataflow.spec.lake.Lake;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.dataflow.workspace.serving.ClickHouseDsl.column;
import static org.jooq.impl.DSL.select;

@Service
public class TaskStates {

    private static final Set<String> FINISHED = Set.of("SUCCESS", "FAILURE", "CANCELED");
    private static final Pattern TASK_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final Tower tower;
    private final RunService runs;
    private final ClickHouse serving;
    private final LakeFunctions lake;
    private final ObjectMapper json;

    public TaskStates(Tower tower, RunService runs, ClickHouse serving, LakeFunctions lake, ObjectMapper json) {
        this.tower = tower;
        this.runs = runs;
        this.serving = serving;
        this.lake = lake;
        this.json = json;
    }

    public TaskState state(String taskId, String runId, String person) {
        if (!TASK_ID.matcher(taskId).matches()) {
            throw new IllegalArgumentException("Not a task id: " + taskId);
        }
        Map<String, String> tags = tower.tags(runId);
        if (!taskId.equals(tags.get(EngineTasks.TASK_ID_TAG))
                || (person != null && !person.equals(tags.get(RunService.PERSON_TAG)))) {
            throw new AccessDeniedException("Task " + taskId + " is not yours to read");
        }
        String status = runs.summary(runId).status();
        return new TaskState(taskId, runId, status, FINISHED.contains(status) ? result(taskId) : null);
    }

    private JsonNode result(String taskId) {
        String raw = serving.fetchValue(
                select(column("raw_blob", String.class)).from(lake.blob(Lake.taskKey(taskId))), String.class);
        if (raw == null) {
            return null;
        }
        try {
            return json.readTree(raw);
        } catch (JacksonException e) {
            throw new IllegalStateException("Task " + taskId + " wrote a result that is not JSON", e);
        }
    }
}
