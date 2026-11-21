package com.dataflow.spec;

import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.parser.CatalystSqlParser;
import org.apache.spark.sql.catalyst.parser.CatalystSqlParser$;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DateType;
import org.apache.spark.sql.types.LongType;
import org.apache.spark.sql.types.TimestampNTZType;
import org.apache.spark.sql.types.TimestampType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class PipelineSpecValidator {

    static final int CURRENT_VERSION = 3;

    private static final Pattern NAME = Pattern.compile(Names.pattern("[a-z0-9_]"));
    private static final Pattern COLUMN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,127}");
    private static final Pattern ORDER_BY = Pattern.compile(
            "\\(?\\s*\\w+(\\(\\w+\\))?(\\s*,\\s*\\w+(\\(\\w+\\))?)*\\s*\\)?");
    private static final Pattern CRON = Pattern.compile("\\S+( \\S+){4}");
    private static final Pattern TRANSFORMATION = Pattern.compile(Names.pattern("[a-z0-9_]") + "@[1-9][0-9]{0,5}");
    private static final Pattern INPUT = Pattern.compile(Names.pattern("[A-Za-z0-9]"));
    private static final Pattern ERROR_CLASS = Pattern.compile("^\\[[A-Z0-9_.]+] ");
    private static final Pattern SQL_STATE = Pattern.compile("\\.? SQLSTATE: \\w+");
    private static final CatalystSqlParser PARSER = CatalystSqlParser$.MODULE$;

    private final List<String> problems = new ArrayList<>();

    private PipelineSpecValidator() {}

    static List<String> validate(PipelineSpec plan) {
        PipelineSpecValidator validator = new PipelineSpecValidator();
        validator.check(plan);
        return List.copyOf(validator.problems);
    }

    private static DataType typeOrNull(String ddl) {
        try {
            return DataType.fromDDL(ddl);
        } catch (Exception e) {
            return null;
        }
    }

    private static String reason(String message) {
        if (message == null) {
            return "";
        }
        String first = message.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("");
        return SQL_STATE.matcher(ERROR_CLASS.matcher(first).replaceFirst("")).replaceFirst("");
    }

    private void check(PipelineSpec plan) {
        require(
                plan.dataset() != null && NAME.matcher(plan.dataset()).matches(),
                "dataset must match " + NAME + ", got '" + plan.dataset() + "'");
        require(plan.version() >= CURRENT_VERSION, "version must be " + CURRENT_VERSION + " or more");
        require(
                plan.schedule() == null || CRON.matcher(plan.schedule().trim()).matches(),
                "schedule must be a five-field cron expression, got '" + plan.schedule() + "'");
        if (plan.ingests()) {
            checkIngestion(plan);
        } else {
            checkDerived(plan);
        }
        checkReferences(plan.references());
        checkInputs(plan.inputs());
        unique(
                "name",
                Stream.of(
                                plan.ingests() ? Stream.of(ServeSpec.CURRENT) : Stream.<String>empty(),
                                plan.references().stream().map(ReferenceSpec::name).distinct(),
                                plan.inputs().keySet().stream(),
                                plan.gold().stream().map(GoldSpec::name).distinct())
                        .flatMap(Function.identity()),
                " (current, references, inputs and gold tables share one set of names)");
        checkGold(plan);
        checkServe(plan);
        problems.addAll(plan.compute().problems());
    }

    private void checkIngestion(PipelineSpec plan) {
        checkSource(plan.source());
        require(plan.bronze().maxFilesPerTrigger() >= 1, "bronze.max-files-per-trigger must be 1 or more");

        SilverSpec silver = plan.silver();
        require(!silver.columns().isEmpty(), "silver.columns must declare at least one column");
        checkColumns("silver.columns", silver.columns());
        checkSteps(silver.steps(), plan.references());
        checkColumns("silver.derive", silver.derive());
        unique(
                "column",
                Stream.of(silver.columns(), silver.added(), silver.derive())
                        .flatMap(List::stream)
                        .map(ColumnSpec::name),
                "");
        checkExpectations(silver.expectations());

        Map<String, DataType> types = new HashMap<>();
        Stream.of(silver.columns(), silver.added(), silver.derive())
                .flatMap(List::stream)
                .filter(column -> column.name() != null && column.type() != null)
                .forEach(column -> types.put(column.name(), typeOrNull(column.type())));
        checkHistory(plan.history(), types);
    }

    private void checkDerived(PipelineSpec plan) {
        SilverSpec silver = plan.silver();
        require(
                silver.columns().isEmpty()
                        && silver.steps().isEmpty()
                        && silver.derive().isEmpty()
                        && silver.expectations().isEmpty()
                        && plan.history().keys().isEmpty()
                        && plan.bronze().equals(BronzeSpec.DEFAULT),
                "bronze, silver and history need a source; a pipeline without source builds gold tables from its"
                        + " inputs and references");
        require(
                !plan.gold().isEmpty(),
                "a pipeline without source needs at least one gold table, built from its inputs or references");
        require(
                !plan.inputs().isEmpty() || !plan.references().isEmpty(),
                "a pipeline without source needs inputs (other datasets' tables) or references to read");
    }

    private void checkInputs(Map<String, String> inputs) {
        inputs.forEach((alias, table) -> {
            matches(NAME, "input name", alias);
            require(
                    TableRef.parse(table).isPresent(),
                    "input " + alias + " must read " + TableRef.FORMAT + ", got '" + table + "'");
        });
    }

    private void checkSource(SourceSpec source) {
        if (source == null) {
            problems.add("source is required");
            return;
        }
        checkFile("source", source);
        PartitionsSpec partitions = source.partitions();
        if (partitions == null) {
            return;
        }
        if ((partitions.monthly() == null) == (partitions.daily() == null)) {
            problems.add("source.partitions needs exactly one of monthly and daily");
            return;
        }
        String where = "source.partitions." + partitions.kind().name().toLowerCase();
        require(
                partitions.kind().accepts(partitions.start()),
                where + ".start '" + partitions.start() + "' does not match the partition key format");
        String location = source.url() != null ? source.url() : source.path();
        require(
                location != null && location.contains(SourceSpec.PARTITION_PLACEHOLDER),
                "a partitioned source must contain " + SourceSpec.PARTITION_PLACEHOLDER + " in its url or path");
    }

    private void checkFile(String where, FileSource file) {
        require(file.url() == null ^ file.path() == null, where + " needs exactly one of url and path");
        require(file.format() != null && !file.format().isBlank(), where + ".format is required");
    }

    private void checkColumns(String where, List<ColumnSpec> columns) {
        for (ColumnSpec column : columns) {
            String at = where + "." + column.name();
            matches(COLUMN, where + " column name", column.name());
            notReserved(at, column.name() == null || !SystemColumns.reserved(column.name()));
            if (column.type() == null) {
                problems.add(at + ".type is required");
            } else {
                parse(at + ".type", column.type(), DataType::fromDDL);
            }
            if (column.from() == null) {
                problems.add(at + ".from is required");
            } else {
                expression(at + ".from", column.from());
            }
        }
    }

    private void checkSteps(List<StepSpec> steps, List<ReferenceSpec> references) {
        List<String> declared = references.stream().map(ReferenceSpec::name).toList();
        for (StepSpec step : steps) {
            String at = "silver.steps." + step.name();
            matches(NAME, "silver.steps name", step.name());
            require(
                    step.transformation() != null
                            && TRANSFORMATION.matcher(step.transformation()).matches(),
                    at + ".transformation must read <name>@<version>, got '" + step.transformation() + "'");
            step.inputs().forEach((input, source) -> checkStepInput(inputAt(at, input), input, source, declared));
            step.adds().forEach(added -> checkStepColumn(at, added));
        }
        unique("step", steps.stream().map(StepSpec::name), "");
    }

    private void checkStepInput(String at, String input, String source, List<String> references) {
        require(INPUT.matcher(input).matches(), at + ": an input is named like a Java record field");
        if (source == null) {
            problems.add(at + " needs a source");
        } else if (source.startsWith(StepSpec.REFERENCE)) {
            String reference = source.substring(StepSpec.REFERENCE.length());
            require(references.contains(reference), at + ": reference '" + reference + "' is not declared under references");
        } else {
            require(
                    source.equals(StepSpec.ROWS) || TableRef.parse(source).isPresent(),
                    at + ": source '" + source + "' must be rows, reference.<name> or " + TableRef.FORMAT);
        }
    }

    private void checkStepColumn(String at, ColumnSpec added) {
        String column = at + ".adds." + added.name();
        matches(COLUMN, at + ".adds column name", added.name());
        notReserved(column, added.name() == null || !SystemColumns.reserved(added.name()));
        require(added.from() == null, column + ": a step's column comes from its transformation, not from 'from'");
        if (added.type() == null) {
            problems.add(column + ".type is required");
        } else {
            parse(column + ".type", added.type(), DataType::fromDDL);
        }
    }

    private void checkExpectations(List<ExpectationSpec> expectations) {
        for (ExpectationSpec expectation : expectations) {
            matches(NAME, "expectation name", expectation.name());
            if (expectation.check() == null) {
                problems.add("expectation " + expectation.name() + " needs a check");
            } else {
                expression("expectation " + expectation.name(), expectation.check());
            }
        }
        unique("expectation", expectations.stream().map(ExpectationSpec::name), "");
    }

    private void checkHistory(HistorySpec history, Map<String, DataType> types) {
        require(!history.keys().isEmpty(), "history.keys must name at least one column");
        require(
                history.keepClosedDays() == null || history.keepClosedDays() >= 1,
                "history.keep-closed-days must be at least 1, got " + history.keepClosedDays());
        history.keys().forEach(name -> declared(types, "history key", name));
        history.trackHistoryExcept()
                .forEach(name -> declared(types, "history.track-history-except column", name));
        String sequence = history.sequenceBy();
        if (sequence == null) {
            return;
        }
        if (!types.containsKey(sequence)) {
            problems.add("history.sequence-by column '" + sequence + "' is not declared");
            return;
        }
        DataType type = types.get(sequence);
        require(
                type instanceof TimestampType
                        || type instanceof TimestampNTZType
                        || type instanceof DateType
                        || type instanceof LongType,
                "history.sequence-by column '" + sequence + "' must be a timestamp, date or bigint");
    }

    private void checkReferences(List<ReferenceSpec> references) {
        for (ReferenceSpec reference : references) {
            matches(NAME, "reference name", reference.name());
            require(
                    !ServeSpec.CURRENT.equals(reference.name()),
                    "reference name '" + ServeSpec.CURRENT + "' is reserved");
            checkFile("reference " + reference.name(), reference);
        }
        unique("reference", references.stream().map(ReferenceSpec::name), "");
    }

    private void checkGold(PipelineSpec plan) {
        TableGraph graph = plan.tables();
        for (GoldSpec table : plan.gold()) {
            matches(NAME, "gold name", table.name());
            String at = "gold " + table.name();
            switch (table.body()) {
                case INVALID -> problems.add(at + " needs exactly one of sql, transformation and from");
                case SQL -> {
                    parse(at, table.sql(), PARSER::parsePlan);
                    require(SqlQueries.single(table.sql()).isPresent(), at + ": " + SqlQueries.RULE);
                    require(
                            table.inputs().isEmpty() && table.params().isEmpty(),
                            at + ": inputs and params go with a transformation");
                }
                case TRANSFORMATION -> checkGoldTransformation(at, table);
                case STEPS -> checkGoldSteps(at, table);
            }
            require(
                    table.steps().isEmpty() || table.usesSteps(),
                    at + ": steps start from a table, so they need from");
            for (String missing : graph.unknown(table.name())) {
                problems.add(at + " reads '" + missing + "', which is not "
                        + (plan.ingests() ? "current, " : "")
                        + "a reference, an input or a gold table of this pipeline"
                        + (TableRef.parse(missing).isPresent()
                                ? "; name it under inputs, for example inputs: { " + missing.replace('.', '_')
                                        + ": " + missing + " }"
                                : ""));
            }
        }
        List<String> cycle = graph.cycle();
        require(cycle.isEmpty(), "gold tables read each other in a loop: " + String.join(" -> ", cycle));
        unique("gold table", plan.gold().stream().map(GoldSpec::name), "");
    }

    private void checkGoldTransformation(String at, GoldSpec table) {
        require(
                TRANSFORMATION.matcher(table.transformation()).matches(),
                at + ".transformation must read <name>@<version>, got '" + table.transformation() + "'");
        table.inputs().forEach((input, source) -> {
            require(
                    INPUT.matcher(input).matches(),
                    inputAt(at, input) + ": an input is named like a Java record field");
            if (source == null) {
                problems.add(inputAt(at, input) + " needs a source");
            }
        });
    }

    private void checkGoldSteps(String at, GoldSpec table) {
        require(
                table.inputs().isEmpty() && table.params().isEmpty(),
                at + ": inputs and params go with a transformation");
        for (int index = 0; index < table.steps().size(); index++) {
            GoldStepSpec step = table.steps().get(index);
            String where = at + " step " + (index + 1);
            List<GoldStepSpec.Operation> operations = step.operations();
            if (operations.size() != 1) {
                problems.add(where + " needs exactly one of "
                        + Stream.of(GoldStepSpec.Operation.values())
                                .map(GoldStepSpec.Operation::key)
                                .toList()
                        + (operations.isEmpty() ? "" : ", found " + operations.stream()
                                .map(GoldStepSpec.Operation::key)
                                .toList()));
                continue;
            }
            checkGoldStep(where, step, operations.getFirst());
        }
    }

    private void checkGoldStep(String where, GoldStepSpec step, GoldStepSpec.Operation operation) {
        String at = where + " (" + operation.key() + ")";
        switch (operation) {
            case JOIN -> checkJoin(at, step.join());
            case UNION -> require(!step.union().isEmpty(), at + " names at least one table");
            case FILTER -> expression(at, step.filter());
            case DERIVE -> {
                namesAColumn(at, step.derive().isEmpty());
                step.derive().forEach((name, sql) -> {
                    outputColumn(at, name);
                    expression(at + " " + name, sql);
                });
            }
            case SELECT -> {
                namesAColumn(at, step.select().isEmpty());
                step.select().forEach(sql -> expression(at, sql));
            }
            case RENAME -> {
                namesAColumn(at, step.rename().isEmpty());
                step.rename().values().forEach(name -> outputColumn(at, name));
            }
            case DROP -> namesAColumn(at, step.drop().isEmpty());
            case AGGREGATE -> {
                AggregateSpec aggregate = step.aggregate();
                require(!aggregate.measures().isEmpty(), at + " needs at least one measure");
                aggregate.by().forEach(sql -> expression(at + " by", sql));
                aggregate.measures().forEach((name, sql) -> {
                    outputColumn(at, name);
                    expression(at + " " + name, sql);
                });
            }
            case DEDUPE -> {
                require(!step.dedupe().keys().isEmpty(), at + " needs at least one key");
                if (step.dedupe().latestBy() != null) {
                    expression(at + " latest-by", step.dedupe().latestBy());
                }
            }
        }
    }

    private void checkJoin(String at, JoinSpec join) {
        require(join.with() != null && !join.with().isBlank(), at + " needs with, the table to join");
        require(
                join.on().isEmpty() != (join.condition() == null),
                at + " needs exactly one of on (matching columns) and condition");
        join.on().forEach((left, right) -> require(
                !left.isBlank() && right != null && !right.isBlank(), at + " on pairs a left and a right column"));
        if (join.condition() != null) {
            expression(at + " condition", join.condition());
        }
        if (join.prefix() != null) {
            matches(COLUMN, at + " prefix", join.prefix());
        }
        require(
                join.prefix() == null || join.type().keepsRight(),
                at + ": " + join.type().name().toLowerCase(Locale.ROOT) + " joins keep no right columns, so no prefix");
    }

    private void outputColumn(String at, String name) {
        matches(COLUMN, at + " column name", name);
        notReserved(at + " column " + SystemColumns.COMPUTED_AT, name == null || !SystemColumns.COMPUTED_AT.equals(name));
    }

    private void checkServe(PipelineSpec plan) {
        Set<String> sources = new HashSet<>();
        if (plan.ingests()) {
            sources.add(ServeSpec.CURRENT);
        }
        plan.materialized().forEach(table -> sources.add(ServeSpec.GOLD_PREFIX + table.name()));
        for (ServeSpec serve : plan.serve()) {
            require(
                    sources.contains(serve.from()),
                    "serve.from '" + serve.from() + "' must be one of "
                            + sources.stream().sorted().toList());
            matches(NAME, "serve table", serve.table());
            require(
                    serve.mode() != ServeSpec.Mode.COPY
                            || (serve.orderBy() != null && !serve.orderBy().isBlank()),
                    "serve " + serve.table() + " is a copy and needs order-by");
            require(
                    serve.orderBy() == null || serve.orderBy().isBlank() || ORDER_BY.matcher(serve.orderBy().strip()).matches(),
                    "serve " + serve.table() + " order-by is a list of columns, such as (day, region)");
        }
        unique("serve table", plan.serve().stream().map(ServeSpec::table), "");
    }

    private void expression(String where, String sql) {
        parse(where, sql, text -> {
            Expression parsed = PARSER.parseExpression(text);
            require(!SqlRelations.hasSubquery(parsed), where + ": an expression may not read other tables: '" + text + "'");
            return parsed;
        });
    }

    private void parse(String where, String text, Function<String, ?> parser) {
        try {
            parser.apply(text);
        } catch (Exception e) {
            String reason = reason(e.getMessage());
            problems.add(where + ": cannot parse '" + text + "'" + (reason.isEmpty() ? "" : ": " + reason));
        }
    }

    private void unique(String what, Stream<String> names, String note) {
        Set<String> seen = new HashSet<>();
        names.filter(name -> name != null && !seen.add(name))
                .distinct()
                .forEach(name -> problems.add(what + " '" + name + "' is declared twice" + note));
    }

    private static String inputAt(String at, String input) {
        return at + ".inputs." + input;
    }

    private void matches(Pattern pattern, String what, String value) {
        require(value != null && pattern.matcher(value).matches(), what + " '" + value + "' must match " + pattern);
    }

    private void notReserved(String what, boolean condition) {
        require(condition, what + " is reserved by the platform");
    }

    private void declared(Map<String, ?> names, String what, String name) {
        require(names.containsKey(name), what + " '" + name + "' is not declared");
    }

    private void namesAColumn(String at, boolean empty) {
        require(!empty, at + " names at least one column");
    }

    private void require(boolean condition, String problem) {
        if (!condition) {
            problems.add(problem);
        }
    }
}
