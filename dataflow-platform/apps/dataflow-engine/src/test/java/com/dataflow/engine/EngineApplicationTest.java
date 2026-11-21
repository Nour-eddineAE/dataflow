package com.dataflow.engine;

import com.dataflow.spec.PipelineSpecs;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class EngineApplicationTest {

    private static final String EXAMPLES = "../../libs/dataflow-spec/src/test/resources/plans/examples";

    @Test
    void validate_startsTheWholeContextAndExitsZero() {
        try (ConfigurableApplicationContext context = SpringApplication.run(
                EngineApplication.class,
                "validate",
                "--spring.profiles.active=spark",
                "--env.pipelines.location=" + EXAMPLES,
                "--env.lake.root=file:./target/lake")) {
            assertThat(SpringApplication.exit(context)).isZero();
        }
    }

    @Test
    void withoutAReleaseOrASpecFolder_startsWithNoPipelines() {
        try (ConfigurableApplicationContext context = SpringApplication.run(
                EngineApplication.class,
                "validate",
                "--spring.profiles.active=spark",
                "--env.pipelines.location=./target/no-pipelines",
                "--env.lake.root=file:./target/lake")) {
            assertThat(context.getBean(PipelineSpecs.class).all()).isEmpty();
            assertThat(SpringApplication.exit(context)).isZero();
        }
    }
}
