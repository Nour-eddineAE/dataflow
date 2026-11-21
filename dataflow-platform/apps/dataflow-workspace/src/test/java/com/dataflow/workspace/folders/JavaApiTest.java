package com.dataflow.workspace.folders;

import com.dataflow.workspace.folders.JavaApi.JavaMember;
import com.dataflow.workspace.folders.JavaApi.JavaType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaApiTest {

    private final List<JavaType> types = new JavaApi().types();

    @Test
    void functions_areStaticWithSimpleTypeNames() {
        assertThat(members("functions"))
                .contains(new JavaMember("col", JavaMember.Kind.METHOD, true, "Column", List.of("String"), false))
                .allMatch(JavaMember::isStatic);
    }

    @Test
    void datasetMethods_keepGenericsAndVarargs_withoutScalaOverloads() {
        assertThat(members("Dataset"))
                .contains(new JavaMember(
                        "withColumn",
                        JavaMember.Kind.METHOD,
                        false,
                        "Dataset<Row>",
                        List.of("String", "Column"),
                        false))
                .contains(new JavaMember(
                        "select", JavaMember.Kind.METHOD, false, "Dataset<Row>", List.of("Column..."), false))
                .noneMatch(member -> member.parameters().stream().anyMatch(type -> type.startsWith("Seq")));
    }

    @Test
    void dataTypes_exposeTheirConstants() {
        assertThat(members("DataTypes"))
                .anyMatch(member -> member.name().equals("StringType")
                        && member.kind() == JavaMember.Kind.FIELD
                        && member.isStatic());
    }

    @Test
    void theDataFlowApi_isDescribed() {
        assertThat(types)
                .filteredOn(type -> type.name().equals("DataTransformation"))
                .singleElement()
                .extracting(JavaType::kind)
                .isEqualTo(JavaType.Kind.ANNOTATION);
        assertThat(members("Lake")).extracting(JavaMember::name).contains("table", "files");
        assertThat(members("DataTransformation")).extracting(JavaMember::name).containsExactly("name", "version");
        assertThat(members("NoParams")).isEmpty();
    }

    private List<JavaMember> members(String type) {
        return types.stream()
                .filter(candidate -> candidate.name().equals(type))
                .findFirst()
                .orElseThrow()
                .members();
    }
}
