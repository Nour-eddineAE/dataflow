package com.dataflow.workspace.folders;

import com.dataflow.transformation.DataTransformation;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public record TransformationInfo(String id, String name, int version, List<String> inputs, List<Param> params) {

    private static final Pattern WORD = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    public record Param(String key, String type) {}

    static Optional<TransformationInfo> of(Class<?> type) {
        DataTransformation annotation = type.getAnnotation(DataTransformation.class);
        return Arrays.stream(type.getGenericInterfaces())
                .filter(ParameterizedType.class::isInstance)
                .map(ParameterizedType.class::cast)
                .filter(generic -> generic.getRawType() == Transformation.class)
                .findFirst()
                .map(generic -> new TransformationInfo(
                        annotation.name() + "@" + annotation.version(),
                        annotation.name(),
                        annotation.version(),
                        components(generic.getActualTypeArguments()[0]).stream()
                                .filter(component -> component.getType() == Dataset.class)
                                .map(RecordComponent::getName)
                                .toList(),
                        components(generic.getActualTypeArguments()[1]).stream()
                                .map(component -> new Param(kebab(component.getName()), component.getType().getSimpleName()))
                                .toList()));
    }

    private static List<RecordComponent> components(Type type) {
        return type instanceof Class<?> record && record.isRecord() ? List.of(record.getRecordComponents()) : List.of();
    }

    private static String kebab(String name) {
        return WORD.matcher(name).replaceAll("-").toLowerCase();
    }
}
