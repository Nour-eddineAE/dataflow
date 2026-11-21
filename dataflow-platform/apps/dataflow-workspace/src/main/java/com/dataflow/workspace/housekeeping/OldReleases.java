package com.dataflow.workspace.housekeeping;

import com.dataflow.workspace.housekeeping.Candidate.Kind;
import com.dataflow.workspace.releases.ReleaseFiles;
import com.dataflow.workspace.releases.ReleaseStore;
import com.dataflow.workspace.releases.ReleaseStore.StoredObject;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OldReleases implements Leftovers {

    private final ReleaseStore releases;
    private final HousekeepingProperties settings;
    private final ObjectMapper json;

    public OldReleases(ReleaseStore releases, HousekeepingProperties settings, ObjectMapper json) {
        this.releases = releases;
        this.settings = settings;
        this.json = json;
    }

    private static Instant newest(List<StoredObject> objects) {
        return objects.stream()
                .map(StoredObject::modified)
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);
    }

    @Override
    public Kind kind() {
        return Kind.RELEASES;
    }

    @Override
    public List<Candidate> candidates() {
        List<Candidate> candidates = new ArrayList<>();
        for (String project : releases.projects()) {
            if (project.equals("system")) {
                continue;
            }
            Map<String, List<StoredObject>> byFolder = new LinkedHashMap<>();
            for (StoredObject object : releases.list(project + "/")) {
                String rest = object.key().substring(project.length() + 1);
                int slash = rest.indexOf('/');
                if (slash > 0) {
                    byFolder.computeIfAbsent(rest.substring(0, slash), folder -> new ArrayList<>())
                            .add(object);
                }
            }
            Set<String> pointed = pointed(byFolder.getOrDefault(ReleaseFiles.TARGETS, List.of()));
            List<Map.Entry<String, List<StoredObject>>> commits = byFolder.entrySet().stream()
                    .filter(folder -> !folder.getKey().equals(ReleaseFiles.TARGETS))
                    .sorted(Comparator.comparing(
                                    (Map.Entry<String, List<StoredObject>> folder) -> newest(folder.getValue()))
                            .reversed())
                    .toList();
            for (int index = settings.keepReleases(); index < commits.size(); index++) {
                Map.Entry<String, List<StoredObject>> commit = commits.get(index);
                if (!pointed.contains(commit.getKey())) {
                    candidates.add(new Candidate(
                            Kind.RELEASES,
                            project + "/" + commit.getKey() + "/",
                            commit.getValue().size(),
                            commit.getValue().stream()
                                    .mapToLong(StoredObject::bytes)
                                    .sum(),
                            commit.getValue().stream().map(StoredObject::key).toList()));
                }
            }
        }
        return candidates;
    }

    @Override
    public void remove(List<String> keys) {
        releases.delete(keys);
    }

    private Set<String> pointed(List<StoredObject> pointers) {
        return pointers.stream()
                .flatMap(pointer -> releases.get(pointer.key()).stream())
                .map(content -> json.readTree(content).path("commit").asString(""))
                .filter(commit -> !commit.isBlank())
                .collect(Collectors.toSet());
    }
}
