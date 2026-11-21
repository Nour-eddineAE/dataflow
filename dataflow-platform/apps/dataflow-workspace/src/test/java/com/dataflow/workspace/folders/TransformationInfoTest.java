package com.dataflow.workspace.folders;

import com.dataflow.workspace.folders.TransformationInfo.Param;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class TransformationInfoTest {

    private final JavaChecks checks = new JavaChecks();

    @Test
    void aTransformation_isDescribedByItsInputsAndTypedParameters() throws IOException {
        List<TransformationInfo> described = checks.describe(Map.of(
                "transformations/EarthquakeEnergy.java", resource("/transformations/EarthquakeEnergy.java")));

        assertThat(described).containsExactly(new TransformationInfo(
                "earthquake_energy@2",
                "earthquake_energy",
                2,
                List.of("quakes", "zones"),
                List.of(new Param("base", "double"), new Param("shallow-km", "int"), new Param("strict", "boolean"),
                        new Param("label", "String"))));
    }

    @Test
    void codeThatDoesNotCompile_describesNothing() {
        assertThat(checks.describe(Map.of("transformations/Broken.java", "class Broken {"))).isEmpty();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = Objects.requireNonNull(TransformationInfoTest.class.getResourceAsStream(path))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
