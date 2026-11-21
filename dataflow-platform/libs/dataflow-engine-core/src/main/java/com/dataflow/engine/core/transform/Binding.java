package com.dataflow.engine.core.transform;

import com.dataflow.spec.StepSpec;
import com.dataflow.transformation.Transformation;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.function.Function;

final class Binding {

    private static final ObjectMapper PARAMS = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private Binding() {}

    static Record inputs(Transformation<?, ?> transformation, StepSpec step, Function<String, Dataset<Row>> sources) {
        Class<?> type = recordType(transformation, 0, step);
        RecordComponent[] components = type.getRecordComponents();
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            if (!Dataset.class.equals(component.getType())) {
                throw StepFailedException.of(step, "input " + component.getName() + " of "
                        + type.getSimpleName() + " must be a Dataset<Row>");
            }
            String source = step.inputs().get(component.getName());
            if (source == null && components.length == 1 && step.inputs().isEmpty()) {
                source = StepSpec.ROWS;
            }
            if (source == null) {
                throw StepFailedException.of(step, "input " + component.getName()
                        + " is not wired; add it under inputs");
            }
            values[i] = sources.apply(source);
        }
        step.inputs().keySet().stream()
                .filter(name -> Arrays.stream(components)
                        .noneMatch(component -> component.getName().equals(name)))
                .findFirst()
                .ifPresent(name -> {
                    throw StepFailedException.of(step, type.getSimpleName() + " has no input " + name);
                });
        return construct(type, components, values, step);
    }

    static Record params(Transformation<?, ?> transformation, StepSpec step) {
        Class<?> type = recordType(transformation, 1, step);
        try {
            return (Record) PARAMS.convertValue(step.params(), type);
        } catch (IllegalArgumentException e) {
            throw StepFailedException.of(step, "params do not fit " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static org.apache.spark.sql.Dataset<Row> apply(Transformation<?, ?> transformation, Record inputs, Record params) {
        return ((Transformation) transformation).apply(inputs, params);
    }

    private static Class<?> recordType(Transformation<?, ?> transformation, int index, StepSpec step) {
        for (Class<?> type = transformation.getClass(); type != null; type = type.getSuperclass()) {
            for (Type implemented : type.getGenericInterfaces()) {
                if (implemented instanceof ParameterizedType parameterized
                        && parameterized.getRawType() == Transformation.class
                        && parameterized.getActualTypeArguments()[index] instanceof Class<?> record
                        && record.isRecord()) {
                    return record;
                }
            }
        }
        throw StepFailedException.of(step, transformation.getClass().getName()
                        + " must be a class implementing Transformation<In, Params> with records as type arguments");
    }

    private static Record construct(Class<?> type, RecordComponent[] components, Object[] values, StepSpec step) {
        try {
            Constructor<?> canonical = type.getDeclaredConstructor(
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
            canonical.setAccessible(true);
            return (Record) canonical.newInstance(values);
        } catch (InvocationTargetException e) {
            throw StepFailedException.of(step, type.getSimpleName() + " refused its inputs: "
                            + e.getCause().getMessage(),
                    e.getCause());
        } catch (ReflectiveOperationException e) {
            throw StepFailedException.of(step, "cannot build " + type.getSimpleName(), e);
        }
    }
}
