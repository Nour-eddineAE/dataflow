package com.dataflow.engine;

import com.dataflow.engine.cli.RunLauncher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConstructorParametersTest {

    private static final int MAX_PARAMETERS = 7;
    private static final String TEST_OUTPUT = "test-classes";

    private static List<Class<?>> mainClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isConcrete() && definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter((reader, factory) -> true);
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.dataflow")) {
            Class<?> type = Class.forName(definition.getBeanClassName(), false, ConstructorParametersTest.class.getClassLoader());
            if (!type.getProtectionDomain().getCodeSource().getLocation().getPath().contains(TEST_OUTPUT)) {
                classes.add(type);
            }
        }
        return classes;
    }

    @Test
    void constructorsLombokWritesTakeAtMostSevenParametersToo() throws ClassNotFoundException {
        List<Class<?>> classes = mainClasses();

        List<String> tooMany = classes.stream()
                .filter(type -> !type.isRecord())
                .flatMap(type -> Arrays.stream(type.getDeclaredConstructors()))
                .filter(constructor -> constructor.getParameterCount() > MAX_PARAMETERS)
                .map(Constructor::toGenericString)
                .toList();

        assertThat(classes).contains(RunLauncher.class);
        assertThat(tooMany).isEmpty();
    }
}
