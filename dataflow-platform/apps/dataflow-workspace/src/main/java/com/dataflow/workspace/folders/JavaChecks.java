package com.dataflow.workspace.folders;

import com.dataflow.spec.compile.CompilationFailedException;
import com.dataflow.spec.compile.TransformationCompiler;
import com.dataflow.workspace.specs.Problem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class JavaChecks {

    public static final String FOLDER = TransformationCompiler.FOLDER + "/";

    private final TransformationCompiler compiler = new TransformationCompiler();

    public static boolean applies(String path) {
        return path.startsWith(FOLDER) && path.endsWith(TransformationCompiler.JAVA);
    }

    private static String flat(String message) {
        return String.join(
                "; ",
                message.lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty())
                        .toList());
    }

    public Result compile(Map<String, String> sources) {
        try {
            return new Result(
                    compiler.compile(sources, getClass().getClassLoader())
                            .byId()
                            .keySet(),
                    List.of());
        } catch (CompilationFailedException e) {
            return new Result(
                    Set.of(),
                    e.diagnoses().stream()
                            .map(diagnosis -> new Located(
                                    diagnosis.path(),
                                    new Problem(
                                            flat(diagnosis.message()),
                                            diagnosis.line() == null
                                                    ? null
                                                    : diagnosis.line().intValue())))
                            .toList());
        }
    }

    public List<TransformationInfo> describe(Map<String, String> sources) {
        try {
            return compiler.compile(sources, getClass().getClassLoader()).byId().values().stream()
                    .map(TransformationInfo::of)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (CompilationFailedException e) {
            return List.of();
        }
    }

    public record Located(String path, Problem problem) {}

    public record Result(Set<String> ids, List<Located> problems) {

        public List<Problem> problemsOf(String path) {
            return problems.stream()
                    .filter(located -> path.equals(located.path()) || located.path() == null)
                    .map(Located::problem)
                    .toList();
        }
    }
}
