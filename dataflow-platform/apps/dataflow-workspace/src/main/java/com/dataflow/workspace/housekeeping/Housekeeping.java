package com.dataflow.workspace.housekeeping;

import com.dataflow.workspace.catalog.LakeFields;
import com.dataflow.workspace.housekeeping.Candidate.Kind;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.specs.StoredSpec;
import com.dataflow.workspace.tasks.EngineTasks;
import com.dataflow.workspace.tasks.TaskStarted;
import org.jooq.Field;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.dataflow.workspace.serving.ClickHouseDsl.column;
import static org.jooq.impl.DSL.select;

@Service
public class Housekeeping {

    static final int MAX_KEYS = 2000;
    private static final Field<String> PATH = column("_path", String.class);
    private static final Field<Long> SIZE = LakeFields.OBJECT_SIZE;
    private static final Field<LocalDateTime> TIME = column("_time", LocalDateTime.class);
    private static final Pattern KEY =
            Pattern.compile("((?:system/tasks|spark-events|system/checkpoints|uploads)/.+)$");
    private static final String TASKS = "system/tasks/";
    private static final String EVENT_LOGS = "spark-events/";
    private static final String CHECKPOINTS = "system/checkpoints/";
    private static final String UPLOADS = "uploads/";

    private final ClickHouse serving;
    private final LakeFunctions lake;
    private final List<Leftovers> leftovers;
    private final SpecStore specs;
    private final EngineTasks tasks;
    private final HousekeepingProperties settings;
    private final Clock clock;

    public Housekeeping(
            ClickHouse serving,
            LakeFunctions lake,
            List<Leftovers> leftovers,
            SpecStore specs,
            EngineTasks tasks,
            HousekeepingProperties settings,
            Clock clock) {
        this.serving = serving;
        this.lake = lake;
        this.leftovers = List.copyOf(leftovers);
        this.specs = specs;
        this.tasks = tasks;
        this.settings = settings;
        this.clock = clock;
    }

    private static String top(String key, String prefix) {
        String rest = key.substring(prefix.length());
        int slash = rest.indexOf('/');
        return prefix + (slash < 0 ? rest : rest.substring(0, slash + 1));
    }

    private static String upload(String key) {
        String person = top(key, UPLOADS);
        return person.endsWith("/") ? top(key, person) : person;
    }

    public List<Candidate> plan() {
        List<Candidate> candidates = new ArrayList<>();
        Instant now = clock.instant();
        previews(now.minus(Duration.ofDays(settings.previewDays()))).ifPresent(candidates::add);
        eventLogs(now.minus(Duration.ofDays(settings.eventLogDays()))).ifPresent(candidates::add);
        uploads(now.minus(Duration.ofDays(settings.uploadDays()))).ifPresent(candidates::add);
        candidates.addAll(checkpoints());
        leftovers.forEach(kind -> candidates.addAll(kind.candidates()));
        candidates.sort(Comparator.comparing(Candidate::kind));
        return candidates;
    }

    public Applied apply(Set<Kind> kinds, String person) {
        List<Candidate> chosen = plan().stream()
                .filter(candidate -> kinds.contains(candidate.kind()))
                .toList();
        Set<Kind> removedHere = leftovers.stream().map(Leftovers::kind).collect(Collectors.toSet());
        int removed = 0;
        for (Leftovers kind : leftovers) {
            List<String> keys = keys(chosen.stream().filter(candidate -> candidate.kind() == kind.kind()));
            if (!keys.isEmpty()) {
                kind.remove(keys);
                removed += keys.size();
            }
        }
        List<String> lakeKeys = keys(chosen.stream().filter(candidate -> !removedHere.contains(candidate.kind())))
                .stream()
                .limit(MAX_KEYS)
                .toList();
        TaskStarted task = lakeKeys.isEmpty() ? null : tasks.clean(lakeKeys, person);
        return new Applied(task, lakeKeys.size(), removed);
    }

    private static List<String> keys(Stream<Candidate> candidates) {
        return candidates.flatMap(candidate -> candidate.keys().stream()).toList();
    }

    private Optional<Candidate> previews(Instant before) {
        List<Listed> old = listed(TASKS + "*.json").stream()
                .filter(file -> file.modified().isBefore(before))
                .toList();
        return old.isEmpty()
                ? Optional.empty()
                : Optional.of(new Candidate(
                        Kind.PREVIEWS,
                        TASKS,
                        old.size(),
                        old.stream().mapToLong(Listed::bytes).sum(),
                        old.stream().map(Listed::key).toList()));
    }

    private Optional<Candidate> eventLogs(Instant before) {
        Map<String, List<Listed>> byApplication = listed(EVENT_LOGS + "**").stream()
                .collect(Collectors.groupingBy(file -> top(file.key(), EVENT_LOGS), TreeMap::new, Collectors.toList()));
        byApplication.remove(EVENT_LOGS + ".keep");
        return untouchedSince(Kind.EVENT_LOGS, EVENT_LOGS, byApplication, before);
    }

    private Optional<Candidate> uploads(Instant before) {
        List<String> read = specs.all().stream().map(StoredSpec::content).toList();
        Map<String, List<Listed>> byUpload = listed(UPLOADS + "**").stream()
                .collect(Collectors.groupingBy(file -> upload(file.key()), TreeMap::new, Collectors.toList()));
        byUpload.keySet().removeIf(folder -> !folder.endsWith("/")
                || read.stream().anyMatch(content -> content.contains(folder)));
        return untouchedSince(Kind.UPLOADS, UPLOADS, byUpload, before);
    }

    private static Optional<Candidate> untouchedSince(
            Kind kind, String location, Map<String, List<Listed>> groups, Instant before) {
        List<String> keys = new ArrayList<>();
        long files = 0;
        long bytes = 0;
        for (Map.Entry<String, List<Listed>> group : groups.entrySet()) {
            Instant last = group.getValue().stream()
                    .map(Listed::modified)
                    .max(Comparator.naturalOrder())
                    .orElse(Instant.MAX);
            if (last.isBefore(before)) {
                keys.add(group.getKey());
                files += group.getValue().size();
                bytes += group.getValue().stream().mapToLong(Listed::bytes).sum();
            }
        }
        return keys.isEmpty() ? Optional.empty() : Optional.of(new Candidate(kind, location, files, bytes, keys));
    }

    private List<Candidate> checkpoints() {
        Set<String> known = specs.all().stream().map(StoredSpec::dataset).collect(Collectors.toSet());
        Map<String, List<Listed>> byDataset = listed(CHECKPOINTS + "**").stream()
                .collect(
                        Collectors.groupingBy(file -> top(file.key(), CHECKPOINTS), TreeMap::new, Collectors.toList()));
        List<Candidate> candidates = new ArrayList<>();
        byDataset.forEach((prefix, files) -> {
            String dataset = prefix.substring(CHECKPOINTS.length(), prefix.length() - 1);
            if (!known.contains(dataset)) {
                candidates.add(new Candidate(
                        Kind.CHECKPOINTS,
                        prefix,
                        files.size(),
                        files.stream().mapToLong(Listed::bytes).sum(),
                        List.of(prefix)));
            }
        });
        return candidates;
    }

    private List<Listed> listed(String pattern) {
        List<Listed> files = new ArrayList<>();
        for (List<Object> row : serving.fetch(select(PATH, SIZE, TIME).from(lake.objects(pattern)), Integer.MAX_VALUE)
                .rows()) {
            Matcher matcher = KEY.matcher(String.valueOf(row.get(0)));
            if (matcher.find()) {
                long bytes = row.get(1) instanceof Number number ? number.longValue() : 0;
                Instant modified = row.get(2) == null
                        ? Instant.MAX
                        : LocalDateTime.parse(String.valueOf(row.get(2)).replace(' ', 'T'))
                                .toInstant(ZoneOffset.UTC);
                files.add(new Listed(matcher.group(1), bytes, modified));
            }
        }
        return files;
    }

    public record Applied(TaskStarted lakeTask, int lakeLocations, int removed) {}

    private record Listed(String key, long bytes, Instant modified) {}
}
