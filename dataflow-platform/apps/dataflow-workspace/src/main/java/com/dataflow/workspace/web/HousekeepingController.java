package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.housekeeping.Candidate;
import com.dataflow.workspace.housekeeping.Housekeeping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/housekeeping")
public class HousekeepingController {

    private final Housekeeping housekeeping;
    private final Access access;

    public HousekeepingController(Housekeeping housekeeping, Access access) {
        this.housekeeping = housekeeping;
        this.access = access;
    }

    @GetMapping
    public List<Item> plan() {
        access.requireAdmin();
        return housekeeping.plan().stream()
                .map(candidate ->
                        new Item(candidate.kind(), candidate.location(), candidate.files(), candidate.bytes()))
                .toList();
    }

    @PostMapping
    public Housekeeping.Applied apply(@RequestBody Request request) {
        access.requireAdmin();
        if (request.kinds() == null || request.kinds().isEmpty()) {
            throw new IllegalArgumentException("Choose what to clean");
        }
        return housekeeping.apply(request.kinds(), access.caller().id());
    }

    public record Item(Candidate.Kind kind, String location, long files, long bytes) {}

    public record Request(Set<Candidate.Kind> kinds) {}
}
