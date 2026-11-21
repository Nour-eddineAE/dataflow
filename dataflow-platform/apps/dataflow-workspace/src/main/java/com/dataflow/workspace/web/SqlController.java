package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.ClickHouseRoles;
import com.dataflow.workspace.queries.SavedQueries;
import com.dataflow.workspace.queries.SavedQuery;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.FunctionDoc;
import com.dataflow.workspace.serving.QueryResult;
import com.dataflow.workspace.serving.QueryRun;
import com.dataflow.workspace.serving.SqlFunction;
import com.dataflow.workspace.serving.SystemTables;
import com.dataflow.workspace.serving.UnknownFunctionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sql")
public class SqlController {

    private static final int DEFAULT_LIMIT = 1000;
    private static final int MAX_HISTORY = 500;
    private static final int MAX_HISTORY_DAYS = 30;

    private final ClickHouse serving;
    private final SystemTables system;
    private final Access access;
    private final ClickHouseRoles roles;
    private final SavedQueries saved;
    private final Clock clock;

    public SqlController(
            ClickHouse serving, SystemTables system, Access access, ClickHouseRoles roles, SavedQueries saved, Clock clock) {
        this.saved = saved;
        this.clock = clock;
        this.access = access;
        this.roles = roles;
        this.serving = serving;
        this.system = system;
    }

    @PostMapping
    public QueryResult run(@RequestBody Statement statement) {
        if (statement.sql() == null || statement.sql().isBlank()) {
            throw new IllegalArgumentException("Write a query first");
        }
        int limit = statement.limit() == null ? DEFAULT_LIMIT : Limits.clamp(statement.limit(), serving.maxRows());
        Caller caller = access.caller();
        return caller.admin()
                ? serving.query(statement.sql(), limit, serving.platform(ClickHouse.editorComment(caller.id())))
                : serving.query(statement.sql(), limit, roles.as(caller));
    }

    @GetMapping("/history")
    public List<QueryRun> history(
            @RequestParam(defaultValue = "7") int days, @RequestParam(defaultValue = "200") int limit) {
        Caller caller = access.caller();
        return system.history(
                caller.admin() ? null : caller.id(),
                LocalDate.now(clock).minusDays(Limits.clamp(days, MAX_HISTORY_DAYS)),
                Limits.clamp(limit, MAX_HISTORY));
    }

    @PostMapping("/format")
    public Statement format(@RequestBody Statement statement) {
        if (statement.sql() == null || statement.sql().isBlank()) {
            return statement;
        }
        return new Statement(serving.format(statement.sql()), statement.limit());
    }

    @GetMapping("/functions")
    public List<SqlFunction> functions() {
        return system.functions();
    }

    @GetMapping("/functions/{name}")
    public FunctionDoc function(@PathVariable String name) {
        return system.functionDoc(name).orElseThrow(() -> new UnknownFunctionException(name));
    }

    @GetMapping("/saved")
    public List<SavedQuery> saved() {
        return saved.list();
    }

    @GetMapping("/saved/{id}")
    public SavedQuery savedQuery(@PathVariable UUID id) {
        return saved.get(id);
    }

    @PostMapping("/saved")
    public SavedQuery save(@RequestBody SaveQuery body) {
        return saved.save(body.id(), body.name(), body.sql(), body.shared());
    }

    @DeleteMapping("/saved/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSaved(@PathVariable UUID id) {
        saved.delete(id);
    }

    public record SaveQuery(UUID id, String name, String sql, boolean shared) {}

    public record Statement(String sql, Integer limit) {}
}
