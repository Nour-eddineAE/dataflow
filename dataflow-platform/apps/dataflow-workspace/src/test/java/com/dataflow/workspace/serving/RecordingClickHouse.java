package com.dataflow.workspace.serving;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.config.WorkspaceConfiguration;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.ResultQuery;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class RecordingClickHouse extends ClickHouse {

    private static final DSLContext SQL = new WorkspaceConfiguration().clickHouseSql();

    private final List<String> statements = new ArrayList<>();
    private final List<Map<String, String>> settings = new ArrayList<>();
    private final Map<Predicate<String>, QueryResult> answers = new LinkedHashMap<>();

    public RecordingClickHouse() {
        super(Workspace.properties(), HttpClient.newHttpClient(), new ObjectMapper(), SQL);
    }

    public static String render(Query query) {
        return SQL.renderInlined(query);
    }

    public RecordingClickHouse answer(Predicate<String> statement, List<String> columns, List<List<Object>> rows) {
        return answer(
                statement,
                new QueryResult(
                        columns.stream()
                                .map(name -> new QueryResult.Column(name, "String"))
                                .toList(),
                        rows,
                        false,
                        1,
                        rows.size(),
                        0));
    }

    public RecordingClickHouse answer(Predicate<String> statement, QueryResult result) {
        answers.put(statement, result);
        return this;
    }

    public List<String> statements() {
        return statements;
    }

    public List<Map<String, String>> settings() {
        return settings;
    }

    @Override
    public QueryResult fetch(ResultQuery<?> query, int maxRows, Map<String, String> applied) {
        settings.add(applied);
        return fetch(query, maxRows);
    }

    @Override
    public QueryResult fetch(ResultQuery<?> query, int maxRows) {
        String statement = render(query);
        statements.add(statement);
        return answers.entrySet().stream()
                .filter(answer -> answer.getKey().test(statement))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> new QueryResult(List.of(), List.of(), false, 1, 0, 0));
    }

    @Override
    public void execute(Query statement) {
        statements.add(render(statement));
    }
}
