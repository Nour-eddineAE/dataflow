package com.dataflow.engine.cli;

import com.dataflow.engine.core.run.RunReport;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EngineCommand implements ApplicationRunner, ExitCodeGenerator {

    static final int SUCCESS = 0;
    static final int FAILURE = 1;
    static final int USAGE = 2;

    static final String RUN = "run";
    static final String VALIDATE = "validate";
    static final String DATASET = "dataset";
    static final String PARTITION = "partition";
    static final String TRIGGER = "trigger";
    static final String FULL_REFRESH = "full-refresh";
    static final String MANUAL = "manual";
    static final String TASK = "task";
    static final String SOURCE = "source";

    private final PipelineSpecs plans;
    private final RunLauncher launcher;
    private final TaskLauncher tasks;
    private volatile int exitCode = SUCCESS;

    private static String option(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static String optionOr(ApplicationArguments args, String name, String fallback) {
        String value = option(args, name);
        return value == null ? fallback : value;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> commands = args.getNonOptionArgs();
        String command = commands.isEmpty() ? VALIDATE : commands.getFirst();
        switch (command) {
            case RUN -> run(
                    option(args, DATASET),
                    option(args, PARTITION),
                    optionOr(args, TRIGGER, MANUAL),
                    args.containsOption(FULL_REFRESH));
            case VALIDATE -> validate();
            default ->
                TaskKind.of(command)
                        .ifPresentOrElse(
                                kind -> task(kind, option(args, DATASET), option(args, TASK), option(args, SOURCE)),
                                () -> {
                                    log.error(
                                            "Unknown command '{}'; use: run --dataset=<id> [--partition=<key>]"
                                                    + " [--trigger=<why>] [--full-refresh] | preview|impact --dataset=<id> --task=<id> |"
                                                    + " infer --dataset=<id> --task=<id> --source=<url> | clean"
                                                    + " --dataset=platform --task=<id> | validate; add"
                                                    + " --release=<project>@<commit> to read the specs of a release",
                                            command);
                                    exitCode = USAGE;
                                });
        }
    }

    private void run(String dataset, String partition, String trigger, boolean fullRefresh) {
        if (dataset == null) {
            log.error(
                    "run needs --dataset=<id>; known: {}",
                    plans.all().stream().map(PipelineSpec::dataset).toList());
            exitCode = USAGE;
            return;
        }
        RunReport report = launcher.launch(dataset, partition, trigger, fullRefresh);
        exitCode = report.completed() ? SUCCESS : FAILURE;
    }

    private void task(TaskKind kind, String dataset, String task, String source) {
        if (dataset == null || task == null) {
            log.error("{} needs --dataset=<id> and --task=<id>", kind.command());
            exitCode = USAGE;
            return;
        }
        if (kind == TaskKind.CLEAN) {
            exitCode = tasks.clean(task) ? SUCCESS : FAILURE;
            return;
        }
        if (kind == TaskKind.INFER) {
            if (source == null) {
                log.error("infer needs --source=<url>");
                exitCode = USAGE;
                return;
            }
            exitCode = tasks.infer(dataset, task, source) ? SUCCESS : FAILURE;
            return;
        }
        exitCode = tasks.launch(kind, dataset, task) ? SUCCESS : FAILURE;
    }

    private void validate() {
        plans.all()
                .forEach(plan -> log.info(
                        "Pipeline spec {} v{} is valid ({} silver columns, {} gold, {} served)",
                        plan.dataset(),
                        plan.version(),
                        plan.silver().columns().size(),
                        plan.gold().size(),
                        plan.serve().size()));
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
