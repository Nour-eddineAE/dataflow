package com.dataflow.workspace.folders;

import com.dataflow.spec.BoardSpec;
import com.dataflow.spec.BoardSpecLoader;
import com.dataflow.spec.InvalidPipelineSpecException;
import com.dataflow.spec.InvalidProjectSpecException;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.ProjectLoader;
import com.dataflow.spec.ProjectSpec;
import com.dataflow.workspace.specs.Parsing;
import com.dataflow.workspace.specs.Problem;
import com.dataflow.workspace.specs.ProblemLocator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FileChecks {

    private static final Pattern PIPELINE =
            Pattern.compile(ProjectSpec.PIPELINES + "/([^/]+)/" + PipelineSpecLoader.PLAN_FILE.replace(".", "\\."));

    private static final Pattern BOARD = Pattern.compile(BoardSpec.FOLDER + "/[a-z][a-z0-9_]*\\.yml");

    private final BoardSpecLoader boards = new BoardSpecLoader();
    private final PipelineSpecLoader specs;
    private final ProjectLoader projects;
    private final Parsing parsing;

    public FileChecks(PipelineSpecLoader specs, Parsing parsing) {
        this.specs = specs;
        this.projects = new ProjectLoader(specs);
        this.parsing = parsing;
    }

    private static List<Problem> located(String content, List<String> problems) {
        return problems.stream()
                .map(problem -> ProblemLocator.locate(content, problem))
                .toList();
    }

    public List<Problem> check(String path, String content) {
        if (content == null) {
            return List.of();
        }
        if (path.equals(ProjectSpec.FILE)) {
            try {
                projects.parse(content, path);
                return List.of();
            } catch (InvalidProjectSpecException e) {
                return located(content, e.problems());
            }
        }
        if (BOARD.matcher(path).matches()) {
            try {
                boards.parse(content, path);
                return List.of();
            } catch (InvalidProjectSpecException e) {
                return located(content, e.problems());
            }
        }
        Matcher pipeline = PIPELINE.matcher(path);
        if (pipeline.matches()) {
            return checkPipeline(pipeline.group(1), content);
        }
        return List.of();
    }

    private List<Problem> checkPipeline(String folder, String content) {
        PipelineSpec spec;
        try {
            spec = parsing.run(() -> specs.parse(content, folder));
        } catch (InvalidPipelineSpecException e) {
            return located(content, e.problems());
        } catch (RuntimeException e) {
            return located(content, List.of(String.valueOf(e.getMessage())));
        }
        if (!folder.equals(spec.dataset())) {
            return located(
                    content, List.of("dataset '" + spec.dataset() + "' must match its folder name '" + folder + "'"));
        }
        return List.of();
    }
}
