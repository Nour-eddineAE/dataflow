package com.dataflow.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

final class SpecYaml {

    private SpecYaml() {}

    static ObjectMapper mapper() {
        return YAMLMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    static ObjectMapper writer() {
        return YAMLMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .serializationInclusion(JsonInclude.Include.NON_EMPTY)
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                .build();
    }

    static String reason(JsonProcessingException e) {
        if (e instanceof UnrecognizedPropertyException unknown) {
            return "unknown key '" + unknown.getPropertyName() + "', expected one of "
                    + unknown.getKnownPropertyIds().stream()
                            .map(String::valueOf)
                            .sorted()
                            .toList();
        }
        return e.getOriginalMessage();
    }
}
