package com.dataflow.workspace.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JsonConfigurationTest {

    private static void run(ContextConsumer<AssertableWebApplicationContext> assertions) throws IOException {
        var properties = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        new WebApplicationContextRunner()
                .withInitializer(
                        context -> properties.forEach(context.getEnvironment().getPropertySources()::addLast))
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class,
                        WebMvcAutoConfiguration.class))
                .run(assertions);
    }

    @Test
    void mvc_usesTheConfiguredJsonMapper() throws IOException {
        run(context -> {
            JsonMapper mapper = context.getBean(JsonMapper.class);
            assertThat(context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters())
                    .filteredOn(JacksonJsonHttpMessageConverter.class::isInstance)
                    .singleElement()
                    .satisfies(converter -> assertThat(((JacksonJsonHttpMessageConverter) converter).getMapper())
                            .isSameAs(mapper));
        });
    }

    @Test
    void responses_omitNullsAndWriteTimesAsText() throws IOException {
        run(context -> assertThat(context.getBean(JsonMapper.class)
                        .writeValueAsString(new Response(
                                "dataflow", null, Instant.parse("2026-10-27T00:00:00Z"), Duration.ofSeconds(90))))
                .isEqualTo("{\"name\":\"dataflow\",\"time\":\"2026-10-27T00:00:00Z\",\"elapsed\":\"PT1M30S\"}"));
    }

    @Test
    void requests_tolerateUnknownFieldsAndMissingNumbers() throws IOException {
        run(context -> assertThat(context.getBean(JsonMapper.class)
                        .readValue("{\"name\":\"dataflow\",\"extra\":true}", Request.class))
                .isEqualTo(new Request("dataflow", 0)));
    }

    record Response(String name, String optional, Instant time, Duration elapsed) {}

    record Request(String name, int limit) {}
}
