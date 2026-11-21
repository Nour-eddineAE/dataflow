package com.dataflow.workspace.config;

import com.dataflow.spec.PipelineSpecLoader;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.BackslashEscaping;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration
public class WorkspaceConfiguration {

    @Bean
    public PipelineSpecLoader pipelineSpecLoader() {
        return new PipelineSpecLoader();
    }

    @Bean
    public HttpClient httpClient(HttpProperties settings) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(settings.connectTimeout())
                .build();
    }

    @Bean
    @SuppressWarnings("UnstableApiUsage")
    public DSLContext clickHouseSql() {
        return DSL.using(
                SQLDialect.CLICKHOUSE,
                new Settings()
                        .withBackslashEscaping(BackslashEscaping.ON)
                        .withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED));
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
