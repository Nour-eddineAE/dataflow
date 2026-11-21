package com.dataflow.workspace.folders;

import com.dataflow.transformation.DataTransformation;
import com.dataflow.transformation.Lake;
import com.dataflow.transformation.NoInputs;
import com.dataflow.transformation.NoParams;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.DataFrameNaFunctions;
import org.apache.spark.sql.DataFrameStatFunctions;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.RelationalGroupedDataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class JavaApi {

    private static final List<Class<?>> TYPES = List.of(
            functions.class,
            Dataset.class,
            Column.class,
            Row.class,
            RelationalGroupedDataset.class,
            DataFrameNaFunctions.class,
            DataFrameStatFunctions.class,
            Window.class,
            WindowSpec.class,
            DataTypes.class,
            SparkSession.class,
            Lake.class,
            Transformation.class,
            DataTransformation.class,
            NoParams.class,
            NoInputs.class);
    private static final Pattern PACKAGE = Pattern.compile("\\b(?:[a-z]\\w*\\.)+(?=[A-Z])");
    private static final String SCALA = "scala.";
    private static final Set<String> GENERATED = Set.of("equals", "hashCode", "toString", "annotationType");

    private volatile List<JavaType> types;

    static JavaType describe(Class<?> type) {
        Map<String, JavaMember> members = new LinkedHashMap<>();
        Stream.concat(
                        Arrays.stream(type.getFields()).filter(JavaApi::visible).map(JavaApi::field),
                        Arrays.stream(type.getMethods())
                                .filter(JavaApi::visible)
                                .filter(method -> !(type.isRecord() || type.isAnnotation())
                                        || !GENERATED.contains(method.getName()))
                                .map(JavaApi::method))
                .sorted(Comparator.comparing(JavaMember::name)
                        .thenComparing(member -> member.parameters().size()))
                .forEach(member -> members.putIfAbsent(member.signature(), member));
        return new JavaType(type.getSimpleName(), type.getName(), kind(type), List.copyOf(members.values()));
    }

    private static boolean visible(Field field) {
        return field.getDeclaringClass() != Object.class
                && !field.getName().contains("$")
                && !field.getType().getName().startsWith(SCALA);
    }

    private static boolean visible(Method method) {
        return method.getDeclaringClass() != Object.class
                && !method.isSynthetic()
                && !method.isBridge()
                && !method.getName().contains("$")
                && Arrays.stream(method.getParameterTypes())
                        .noneMatch(parameter -> parameter.getName().startsWith(SCALA));
    }

    private static JavaMember field(Field field) {
        return new JavaMember(
                field.getName(),
                JavaMember.Kind.FIELD,
                Modifier.isStatic(field.getModifiers()),
                simple(field.getGenericType()),
                List.of(),
                field.isAnnotationPresent(Deprecated.class));
    }

    private static JavaMember method(Method method) {
        Type[] parameters = method.getGenericParameterTypes();
        List<String> names = Arrays.stream(parameters).map(JavaApi::simple).toList();
        if (method.isVarArgs() && !names.isEmpty()) {
            String last = names.getLast();
            names = Stream.concat(
                            names.stream().limit(names.size() - 1L),
                            Stream.of(last.substring(0, last.length() - 2) + "..."))
                    .toList();
        }
        return new JavaMember(
                method.getName(),
                JavaMember.Kind.METHOD,
                Modifier.isStatic(method.getModifiers()),
                simple(method.getGenericReturnType()),
                names,
                method.isAnnotationPresent(Deprecated.class));
    }

    private static String simple(Type type) {
        return PACKAGE.matcher(type.getTypeName().replace('$', '.')).replaceAll("");
    }

    private static JavaType.Kind kind(Class<?> type) {
        if (type.isAnnotation()) {
            return JavaType.Kind.ANNOTATION;
        }
        if (type.isRecord()) {
            return JavaType.Kind.RECORD;
        }
        return type.isInterface() ? JavaType.Kind.INTERFACE : JavaType.Kind.CLASS;
    }

    public List<JavaType> types() {
        if (types == null) {
            types = TYPES.stream().map(JavaApi::describe).toList();
        }
        return types;
    }

    public record JavaType(String name, String qualifiedName, Kind kind, List<JavaMember> members) {

        public enum Kind {
            CLASS,
            INTERFACE,
            RECORD,
            ANNOTATION
        }
    }

    public record JavaMember(
            String name, Kind kind, boolean isStatic, String returns, List<String> parameters, boolean deprecated) {

        String signature() {
            return name + "(" + String.join(",", parameters) + ")" + (isStatic ? "static" : "");
        }

        public enum Kind {
            METHOD,
            FIELD
        }
    }
}
