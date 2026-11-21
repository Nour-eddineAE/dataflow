package com.dataflow.workspace.tasks;

import com.dataflow.spec.Names;
import com.dataflow.spec.ReleaseId;
import com.dataflow.spec.lake.Lake;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.folders.FolderFiles;
import com.dataflow.workspace.releases.DeployedView;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.specs.InvalidDraftException;
import com.dataflow.workspace.specs.SpecCheck;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.specs.StoredSpec;
import java.util.HashMap;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EngineTasks {

    static final String CLEAN = "clean";
    static final String TASK_ID_TAG = "workspace/task-id";

    private static final int MAX_TRANSFORMATIONS = 96 * 1024;

    static final String OP = "run_engine_task";

    private final Tower tower;
    private final SpecStore specs;
    private final ObjectMapper json;
    private final String job;
    private final DeployedView deployed;
    private final FolderFiles folders;
    private final Access access;

    public EngineTasks(
            Tower tower,
            SpecStore specs,
            ObjectMapper json,
            WorkspaceProperties properties,
            DeployedView deployed,
            FolderFiles folders,
            Access access) {
        this.tower = tower;
        this.specs = specs;
        this.json = json;
        this.job = properties.tower().taskJob();
        this.deployed = deployed;
        this.folders = folders;
        this.access = access;
    }

    public TaskStarted start(TaskKind kind, String dataset, String content, UUID folderId) {
        SpecCheck check = specs.check(content == null ? "" : content, dataset);
        if (!check.valid()) {
            throw new InvalidDraftException(dataset, check.problems());
        }
        if (kind == TaskKind.IMPACT
                && specs.find(dataset).filter(StoredSpec::valid).isEmpty()) {
            throw new IllegalArgumentException(dataset + " has no saved spec to compare with; preview it instead");
        }
        assert content != null;
        return launch(kind.command(), dataset, Map.of(
                "draft",
                Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)),
                "release",
                deployed.releaseOf(dataset).map(ReleaseId::toString).orElse(""),
                "transformations",
                folderId == null ? "" : transformations(folderId)));
    }

    public TaskStarted infer(String dataset, String source) {
        if (dataset == null || !Names.DATASET.matcher(dataset).matches()) {
            throw new IllegalArgumentException("A dataset name is a-z, 0-9 and _, starting with a letter");
        }
        String location = source == null ? "" : source.strip();
        if (!Lake.KEY.matcher(location).matches() || location.contains("..")) {
            URI uri;
            try {
                uri = URI.create(location);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Not a URL: " + source);
            }
            if (uri.getHost() == null
                    || !Set.of("http", "https")
                            .contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("A source to sample is an http or https URL, or a path in the lake");
            }
            location = uri.toString();
        }
        return launch(TaskKind.INFER.command(), dataset, Map.of("source", location));
    }

    public TaskStarted clean(List<String> keys, String person) {
        String encoded =
                Base64.getEncoder().encodeToString(String.join("\n", keys).getBytes(StandardCharsets.UTF_8));
        return launch(CLEAN, Names.PLATFORM, Map.of(CLEAN, encoded), person);
    }

    private TaskStarted launch(String command, String dataset, Map<String, Object> input) {
        return launch(command, dataset, input, access.caller().id());
    }

    private TaskStarted launch(String command, String dataset, Map<String, Object> input, String person) {
        String taskId = UUID.randomUUID().toString();
        Map<String, Object> config = new HashMap<>(input);
        config.put("command", command);
        config.put("dataset", dataset);
        config.put("task_id", taskId);
        String runId = tower.launch(
                job,
                Map.of(
                        RunService.TASK_TAG, command,
                        RunService.DATASET_TAG, dataset,
                        TASK_ID_TAG, taskId,
                        RunService.PERSON_TAG, person),
                Map.of("ops", Map.of(OP, Map.of("config", config))));
        return new TaskStarted(taskId, runId);
    }

    private String transformations(UUID folderId) {
        Map<String, String> sources = folders.transformations(folderId);
        try {
            byte[] encoded = Base64.getEncoder().encode(json.writeValueAsBytes(sources));
            if (encoded.length > MAX_TRANSFORMATIONS) {
                throw new IllegalArgumentException("The folder's transformations are too large to send with a preview ("
                        + DataSize.ofBytes(encoded.length).toKilobytes() + " KB)");
            }
            return new String(encoded, StandardCharsets.UTF_8);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }
}
