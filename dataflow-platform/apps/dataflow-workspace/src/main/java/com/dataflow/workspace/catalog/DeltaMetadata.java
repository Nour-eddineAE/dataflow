package com.dataflow.workspace.catalog;

import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import org.jooq.Condition;
import org.jooq.Field;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.StreamSupport;

import static com.dataflow.workspace.serving.ClickHouseDsl.argMaxIf;
import static com.dataflow.workspace.serving.ClickHouseDsl.column;
import static com.dataflow.workspace.serving.ClickHouseDsl.firstMatch;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonHas;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonRaw;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonText;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonUnsigned;
import static com.dataflow.workspace.serving.ClickHouseDsl.minIf;
import static com.dataflow.workspace.serving.ClickHouseDsl.unsigned;
import static org.jooq.impl.DSL.select;

@Component
public class DeltaMetadata {

    private static final String CONFIGURATION = "configuration";
    private static final String META_DATA = "metaData";
    private static final String PROTOCOL = "protocol";
    private static final String DOMAIN = "domainMetadata";
    private static final String CLUSTERING = "delta.clustering";
    private static final Field<String> FILE = column("_file", String.class);
    private static final Field<String> JSON = column("json", String.class);
    private static final TypeReference<Map<String, String>> PROPERTIES = new TypeReference<>() {};

    private final ClickHouse serving;
    private final LakeFunctions lake;
    private final ObjectMapper json;

    public DeltaMetadata(ClickHouse serving, LakeFunctions lake, ObjectMapper json) {
        this.serving = serving;
        this.lake = lake;
        this.json = json;
    }

    private static List<String> clusteringColumns(JsonNode configuration) {
        return StreamSupport.stream(configuration.path("clusteringColumns").spliterator(), false)
                .map(path -> String.join(".", texts(path)))
                .toList();
    }

    private static List<String> features(JsonNode protocol) {
        List<String> features = new ArrayList<>(texts(protocol.path("readerFeatures")));
        texts(protocol.path("writerFeatures")).stream()
                .filter(feature -> !features.contains(feature))
                .forEach(features::add);
        return features;
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asString)
                .toList();
    }

    public DeltaDetails read(String tableKey) {
        Field<Long> version = unsigned(firstMatch(FILE, DeltaLog.VERSION));
        Condition metaData = jsonHas(JSON, META_DATA);
        Condition protocol = jsonHas(JSON, PROTOCOL);
        Condition clustering = jsonText(JSON, DOMAIN, "domain").eq(CLUSTERING);
        Actions latest = serving.fetchInto(
                        select(
                                        argMaxIf(jsonRaw(JSON, META_DATA, CONFIGURATION), version, metaData)
                                                .as(CONFIGURATION),
                                        argMaxIf(jsonRaw(JSON, META_DATA, "partitionColumns"), version, metaData)
                                                .as("partitionColumns"),
                                        minIf(jsonUnsigned(JSON, META_DATA, "createdTime"), metaData)
                                                .as("createdTime"),
                                        argMaxIf(jsonRaw(JSON, PROTOCOL), version, protocol)
                                                .as("protocol"),
                                        argMaxIf(jsonText(JSON, DOMAIN, CONFIGURATION), version, clustering)
                                                .as("clustering"))
                                .from(lake.jsonDocuments(tableKey + DeltaLog.COMMITS)),
                        Actions.class)
                .getFirst();
        JsonNode protocolNode = tree(latest.protocol());
        return new DeltaDetails(
                properties(latest.configuration()),
                texts(tree(latest.partitionColumns())),
                clusteringColumns(tree(latest.clustering())),
                protocolNode.hasNonNull("minReaderVersion")
                        ? protocolNode.get("minReaderVersion").asInt()
                        : null,
                protocolNode.hasNonNull("minWriterVersion")
                        ? protocolNode.get("minWriterVersion").asInt()
                        : null,
                features(protocolNode),
                latest.createdTime() == null || latest.createdTime() == 0
                        ? null
                        : Instant.ofEpochMilli(latest.createdTime()));
    }

    private Map<String, String> properties(String raw) {
        try {
            return raw == null || raw.isBlank() ? Map.of() : new TreeMap<>(json.readValue(raw, PROPERTIES));
        } catch (JacksonException e) {
            return Map.of();
        }
    }

    private JsonNode tree(String raw) {
        try {
            return raw == null || raw.isBlank() ? json.createObjectNode() : json.readTree(raw);
        } catch (JacksonException e) {
            return json.createObjectNode();
        }
    }

    record Actions(
            String configuration, String partitionColumns, Long createdTime, String protocol, String clustering) {}
}
