package com.dataflow.workspace.catalog;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaMetadataTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void read_takesTheLatestMetadataProtocolAndClustering() throws IOException {
        RecordingClickHouse serving = new RecordingClickHouse()
                .answer(
                        sql -> sql.contains("argMaxIf"),
                        List.of("configuration", "partitionColumns", "createdTime", "protocol", "clustering"),
                        List.of(actions()));

        DeltaDetails details = new DeltaMetadata(serving, new LakeFunctions(Workspace.properties()), json)
                .read("history/earthquakes/");

        assertThat(details)
                .isEqualTo(new DeltaDetails(
                        Map.of("delta.enableChangeDataFeed", "true", "delta.enableDeletionVectors", "false"),
                        List.of(),
                        List.of("factUniqueId"),
                        1,
                        7,
                        List.of("domainMetadata", "changeDataFeed", "clustering"),
                        Instant.ofEpochMilli(1792871415809L)));
        assertThat(serving.statements())
                .singleElement()
                .asString()
                .contains("argMaxIf(JSONExtractRaw(\"json\", 'metaData', 'configuration')")
                .contains("filename = 'history/earthquakes/_delta_log/*.json', format = 'JSONAsString'");
    }

    private List<Object> actions() throws IOException {
        try (InputStream stream =
                Objects.requireNonNull(getClass().getResourceAsStream("/catalog/delta-actions.json"))) {
            return json.readValue(stream, new TypeReference<>() {});
        }
    }
}
