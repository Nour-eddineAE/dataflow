package com.dataflow.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class ProjectSpecValidator {

    static final Pattern PROJECT = Pattern.compile(Names.pattern("[a-z0-9-]"));
    private static final Pattern TARGET = Pattern.compile("[a-z][a-z0-9-]{0,30}");
    private static final Pattern BRANCH = Pattern.compile("[A-Za-z0-9._][A-Za-z0-9._/-]{0,199}");

    private final List<String> problems = new ArrayList<>();

    private ProjectSpecValidator() {}

    static List<String> validate(ProjectSpec spec) {
        ProjectSpecValidator validator = new ProjectSpecValidator();
        validator.check(spec);
        return List.copyOf(validator.problems);
    }

    private static boolean validBranch(String branch) {
        return BRANCH.matcher(branch).matches()
                && !branch.contains("..")
                && !branch.endsWith("/")
                && !branch.endsWith(".lock")
                && !branch.contains("//");
    }

    private void check(ProjectSpec spec) {
        require(
                spec.project() != null && PROJECT.matcher(spec.project()).matches(),
                "project must match " + PROJECT + ", got '" + spec.project() + "'");
        require(!spec.targets().isEmpty(), "targets must declare at least one target");
        long production = spec.targets().values().stream()
                .filter(t -> t != null && t.production())
                .count();
        require(production <= 1, "at most one target may be production, found " + production);
        for (Map.Entry<String, TargetSpec> entry : spec.targets().entrySet()) {
            checkTarget(entry.getKey(), entry.getValue());
        }
    }

    private void checkTarget(String name, TargetSpec target) {
        String at = "targets." + name;
        require(TARGET.matcher(name).matches(), at + ": target name must match " + TARGET);
        if (target == null || target.mode() == null) {
            problems.add(at + ".mode must be development or production");
            return;
        }
        if (target.production()) {
            require(target.branch() != null, at + ".branch is required for a production target");
        }
        require(
                target.branch() == null || validBranch(target.branch()),
                at + ".branch '" + target.branch() + "' is not a valid branch name");
        target.compute().problems().forEach(problem -> problems.add(at + "." + problem));
    }

    private void require(boolean condition, String problem) {
        if (!condition) {
            problems.add(problem);
        }
    }
}
