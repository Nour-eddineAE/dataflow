package com.dataflow.transformation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractTest {

    private static final List<Class<?>> PUBLIC_TYPES =
            List.of(DataTransformation.class, Lake.class, NoInputs.class, NoParams.class, Transformation.class);

    private static List<String> surface() {
        return PUBLIC_TYPES.stream()
                .flatMap(type -> Stream.concat(
                        Stream.of(kind(type) + " " + type.getName() + generics(type)),
                        Arrays.stream(type.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers()) && !method.isSynthetic())
                                .map(method -> "  " + type.getSimpleName() + "." + signature(method))
                                .sorted()))
                .toList();
    }

    private static String kind(Class<?> type) {
        return type.isAnnotation()
                ? "annotation"
                : type.isInterface() ? "interface" : type.isRecord() ? "record" : "class";
    }

    private static String generics(Class<?> type) {
        return type.getTypeParameters().length == 0
                ? ""
                : "<"
                        + String.join(
                                ", ",
                                Arrays.stream(type.getTypeParameters())
                                        .map(parameter -> parameter.getName() + " extends "
                                                + String.join(
                                                        " & ",
                                                        Arrays.stream(parameter.getBounds())
                                                                .map(Type::getTypeName)
                                                                .toList()))
                                        .toList())
                        + ">";
    }

    private static String signature(Method method) {
        String defaultValue = method.getDefaultValue() == null ? "" : " default " + method.getDefaultValue();
        return method.getGenericReturnType().getTypeName() + " " + method.getName() + "("
                + String.join(
                        ", ",
                        Arrays.stream(method.getGenericParameterTypes())
                                .map(Type::getTypeName)
                                .toList()) + ")"
                + defaultValue;
    }

    @Test
    void thePublicApiIsExactlyTheOneTransformationsWereWrittenAgainst() throws IOException {
        try (InputStream recorded = Objects.requireNonNull(getClass().getResourceAsStream("/api.txt"))) {
            List<String> expected = new String(recorded.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank())
                    .toList();

            assertThat(surface())
                    .as("The transformation API changed. Removing or changing a member breaks transformations"
                            + " already in projects; add new members instead, then record them in"
                            + " src/test/resources/api.txt")
                    .containsExactlyElementsOf(expected);
        }
    }
}
