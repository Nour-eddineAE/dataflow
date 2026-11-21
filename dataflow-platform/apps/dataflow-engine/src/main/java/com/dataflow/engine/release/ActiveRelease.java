package com.dataflow.engine.release;

import com.dataflow.spec.ReleaseId;
import org.springframework.boot.ApplicationArguments;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ActiveRelease(Optional<ReleaseId> id) {

    public static final String OPTION = "release";

    public static ActiveRelease of(ApplicationArguments args) {
        List<String> values = args.getOptionValues(OPTION);
        return new ActiveRelease(
                values == null || values.isEmpty()
                        ? Optional.empty()
                        : Optional.of(ReleaseId.parse(values.getFirst())));
    }

    public Map<String, String> labels() {
        return id.map(release -> Map.of(OPTION, release.toString())).orElse(Map.of());
    }
}
