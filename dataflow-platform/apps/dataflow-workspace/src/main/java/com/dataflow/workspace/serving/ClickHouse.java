package com.dataflow.workspace.serving;

import com.dataflow.workspace.config.WorkspaceProperties;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.ResultQuery;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectOne;

@Component
public class ClickHouse {

    public static final String EDITOR = "sql:";
    private static final Duration ANSWER_MARGIN = Duration.ofSeconds(5);
    private static final Pattern EXCEPTION =
            Pattern.compile("(?s)Code: \\d+\\. DB::Exception: (?<text>.*?)(?: \\((?<name>[A-Z_]+)\\))?(?: \\(version"
                    + " [^()]*(?:\\([^()]*\\))?[^()]*\\))?\\s*$");
    private static final Pattern NOISE =
            Pattern.compile("(?s)\\s*(Expected one of: .*?(?=: In scope|$)|: In scope .*$)|(?<=\\.)\\s+In scope .*$");
    private static final TypeReference<List<List<Object>>> ROWS = new TypeReference<>() {};

    private final WorkspaceProperties.Serving settings;
    private final HttpClient http;
    private final ObjectMapper json;
    private final DSLContext sql;

    public ClickHouse(WorkspaceProperties properties, HttpClient http, ObjectMapper json, DSLContext sql) {
        this.settings = properties.serving();
        this.http = http;
        this.json = json;
        this.sql = sql;
    }

    private static Map<String, Object> record(QueryResult result, List<Object> row) {
        Map<String, Object> record = new LinkedHashMap<>();
        for (int i = 0; i < result.columns().size(); i++) {
            record.put(result.columns().get(i).name(), row.get(i));
        }
        return record;
    }

    static String strip(String sql) {
        String trimmed = sql.strip();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
        }
        return trimmed;
    }

    static String message(String body) {
        Matcher matcher = EXCEPTION.matcher(body.strip());
        if (matcher.find()) {
            String text = NOISE.matcher(matcher.group("text").strip()).replaceAll("").strip();
            String name = matcher.group("name");
            return name == null ? text : text + " (" + name + ")";
        }
        return body.strip();
    }

    private static String basicAuth(String user, String key) {
        String credentials = (user == null ? "" : user) + ":" + (key == null ? "" : key);
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public int maxRows() {
        return settings.maxRows();
    }

    public int dashboardRows() {
        return settings.dashboardRows();
    }

    public String database() {
        return settings.database();
    }

    public QueryResult fetch(ResultQuery<?> query) {
        return fetch(query, settings.maxRows());
    }

    public QueryResult fetch(ResultQuery<?> query, int maxRows) {
        return post(sql.renderInlined(query), maxRows, null);
    }

    public QueryResult fetch(ResultQuery<?> query, int maxRows, Map<String, String> extra) {
        return post(sql.renderInlined(query), maxRows, null, extra);
    }

    public <T> List<T> fetchInto(ResultQuery<?> query, Class<T> type) {
        return fetchInto(query, type, settings.maxRows());
    }

    public <T> List<T> fetchInto(ResultQuery<?> query, Class<T> type, int maxRows) {
        QueryResult result = fetch(query, maxRows);
        return result.rows().stream()
                .map(row -> json.convertValue(record(result, row), type))
                .toList();
    }

    public <T> List<T> fetchValues(ResultQuery<?> query, Class<T> type) {
        return fetch(query).rows().stream()
                .map(row -> json.convertValue(row.getFirst(), type))
                .toList();
    }

    public <T> T fetchValue(ResultQuery<?> query, Class<T> type) {
        return fetchValues(query, type).stream().findFirst().orElse(null);
    }

    public String render(ResultQuery<?> query) {
        return sql.renderInlined(query);
    }

    public String format(String statement) {
        return fetchValue(select(function("formatQuery", String.class, inline(strip(statement)))), String.class);
    }

    public QueryResult query(String statement, int maxRows) {
        return post(statement, maxRows, null);
    }

    public QueryResult query(String statement, int maxRows, As as) {
        return post(statement, maxRows, as);
    }

    public String password() {
        return settings.password();
    }

    public void execute(Query statement) {
        send(
                sql.renderInlined(statement),
                Map.of("max_execution_time", String.valueOf(settings.timeout().toSeconds())),
                null);
    }

    public boolean reachable() {
        try {
            fetch(selectOne(), 1);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static String editorComment(String person) {
        return EDITOR + person;
    }

    public As platform(String comment) {
        return new As(settings.user(), settings.password(), List.of(), comment);
    }

    public Optional<String> version() {
        try {
            return Optional.ofNullable(fetchValue(select(function("version", String.class)), String.class));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private QueryResult post(String statement, int maxRows, As as) {
        return post(statement, maxRows, as, Map.of());
    }

    private QueryResult post(String statement, int maxRows, As as, Map<String, String> extra) {
        JsonNode body = parse(send(strip(statement), parameters(maxRows, as, extra), as));
        List<QueryResult.Column> columns = new ArrayList<>();
        body.path("meta")
                .forEach(column -> columns.add(new QueryResult.Column(
                        column.path("name").asString(), column.path("type").asString())));
        List<List<Object>> rows = json.convertValue(body.path("data"), ROWS);
        boolean truncated = rows.size() > maxRows;
        JsonNode statistics = body.path("statistics");
        return new QueryResult(
                columns,
                truncated ? List.copyOf(rows.subList(0, maxRows)) : rows,
                truncated,
                Seconds.toMillis(statistics.path("elapsed").asDouble()),
                statistics.path("rows_read").asLong(),
                statistics.path("bytes_read").asLong());
    }

    private Map<String, String> parameters(int maxRows, As as, Map<String, String> extra) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("default_format", "JSONCompact");
        query.put("readonly", "2");
        query.put("max_result_rows", String.valueOf(maxRows + 1L));
        query.put("result_overflow_mode", "break");
        query.put("max_execution_time", String.valueOf(settings.timeout().toSeconds()));
        query.put("output_format_json_quote_64bit_integers", "0");
        query.put("output_format_json_quote_denormals", "1");
        query.put("s3_ignore_file_doesnt_exist", "1");
        if (as != null && as.comment() != null) {
            query.put("log_comment", as.comment());
        }
        query.putAll(extra);
        return query;
    }

    private String send(String sql, Map<String, String> query, As as) {
        String roles = as == null
                ? ""
                : as.roles().stream()
                        .map(role -> "&role=" + URLEncoder.encode(role, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
        String url = settings.url().replaceAll("/+$", "") + "/?"
                + query.entrySet().stream()
                        .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"))
                + roles;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(settings.timeout().plus(ANSWER_MARGIN))
                .header(
                        HttpHeaders.AUTHORIZATION,
                        basicAuth(
                                as == null ? settings.user() : as.user(), as == null ? settings.password() : as.key()))
                .POST(HttpRequest.BodyPublishers.ofString(sql, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HttpStatus.OK.value()) {
                throw new ServingQueryException(message(response.body()));
            }
            return response.body();
        } catch (IOException e) {
            throw new ServingQueryException("Serving (ClickHouse) is not reachable at " + settings.url(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServingQueryException("Interrupted while querying serving", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            throw new ServingQueryException("Serving answered something other than JSON; remove any FORMAT clause", e);
        }
    }

    public record As(String user, String key, List<String> roles, String comment) {}
}
