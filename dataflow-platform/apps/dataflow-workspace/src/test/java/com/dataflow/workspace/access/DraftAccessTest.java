package com.dataflow.workspace.access;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.config.SourcesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DraftAccessTest {

    private static final Caller READER = new Caller("bob", List.of("analysts"), false);
    private static final Caller ADMIN = new Caller("ada", List.of("admins"), true);
    private static final String SILVER = """
            silver:
              columns:
                - { name: id, type: string, required: true, from: id }
            history: { keys: [id] }
            """;
    private static final String READS_INPUT = """
            dataset: draft
            version: 3
            inputs:
              rows: taxi_trips.current
            gold:
              - name: copy
                sql: SELECT * FROM rows
            """;

    private final Access access = mock(Access.class);
    private final DraftAccess drafts = new DraftAccess(access, new SourcesProperties(List.of("files.corp.test"), List.of("s3a://inbox/")));

    private static PipelineSpec source(String location) {
        return new PipelineSpecLoader().parse("""
                dataset: draft
                version: 3
                source:
                  %s
                  format: csv
                """.formatted(location) + SILVER, "draft");
    }

    @BeforeEach
    void signIn() {
        when(access.caller()).thenReturn(READER);
    }

    @Test
    void aDraftReadsAnotherDatasetOnlyWithItsGrant() {
        PipelineSpec spec = new PipelineSpecLoader().parse(READS_INPUT, "draft");
        doThrow(new AccessDeniedException("bob lacks SELECT"))
                .when(access)
                .require(Privilege.SELECT, new Securable(Securable.Kind.TABLE, "taxi_trips.current"));

        assertThatThrownBy(() -> drafts.require(spec)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aReleaseReadsItsOwnProjectWithoutAGrant() {
        PipelineSpec spec = new PipelineSpecLoader().parse(READS_INPUT, "draft");
        doThrow(new AccessDeniedException("bob lacks SELECT"))
                .when(access)
                .require(Privilege.SELECT, new Securable(Securable.Kind.TABLE, "taxi_trips.current"));

        assertThatCode(() -> drafts.require(spec, "taxi_trips"::equals)).doesNotThrowAnyException();
    }

    @Test
    void aPersonSamplesAndDraftsOnlyFromTheirOwnUploads() {
        assertThatCode(() -> drafts.requireSource("uploads/bob/1/a.csv")).doesNotThrowAnyException();
        assertThatCode(() -> drafts.require(source("path: s3a://lake/uploads/bob/1/a.csv"))).doesNotThrowAnyException();

        assertThatThrownBy(() -> drafts.requireSource("uploads/ada/1/a.csv")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> drafts.require(source("path: s3a://lake/uploads/ada/1/a.csv")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> drafts.require(source("path: s3a://lake/uploads/bob/../ada/a.csv")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aPublicAddressIsAllowed() {
        assertThatCode(() -> drafts.require(source("url: https://8.8.8.8/data/a.csv")))
                .doesNotThrowAnyException();
    }

    @Test
    void internalAddressesAreRefused() {
        for (String url : List.of(
                "http://clickhouse:8123/?query=select+1",
                "http://127.0.0.1:9000/lake/",
                "http://10.0.0.5/data.csv",
                "http://169.254.169.254/latest/meta-data/",
                "http://[::1]:8080/",
                "http://100.100.100.200/latest/",
                "http://rustfs.dataflow.svc/lake/")) {
            assertThatThrownBy(() -> drafts.require(source("url: " + url)))
                    .as(url)
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void anAllowedInternalHostIsRead() {
        assertThatCode(() -> drafts.requireSource("https://files.corp.test/data.csv")).doesNotThrowAnyException();
    }

    @Test
    void pathsNeedAnAllowedPrefix() {
        assertThatCode(() -> drafts.require(source("path: s3a://inbox/a/*.csv"))).doesNotThrowAnyException();
        for (String path : List.of(
                "s3a://lake/silver/taxi_trips/*",
                "/var/run/secrets/kubernetes.io/serviceaccount/token",
                "/data/taxi_trips/*",
                "s3a://inbox/../lake/silver/*")) {
            assertThatThrownBy(() -> drafts.require(source("path: \"" + path + "\"")))
                    .as(path)
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void onlyFileFormatsAreRead() {
        PipelineSpec database = new PipelineSpecLoader().parse("""
                dataset: draft
                version: 3
                source:
                  url: https://8.8.8.8/x
                  format: jdbc
                """ + SILVER, "draft");

        assertThatThrownBy(() -> drafts.require(database)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void inferringFromTheLakeNeedsSelectOnThatDataset() {
        doThrow(new AccessDeniedException("bob lacks SELECT"))
                .when(access)
                .require(Privilege.SELECT, Securable.dataset("taxi_trips"));

        assertThatThrownBy(() -> drafts.requireSource("silver/taxi_trips/part-0.parquet"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> drafts.requireSource("silver/earthquakes/part-0.parquet")).doesNotThrowAnyException();
        assertThatThrownBy(() -> drafts.requireSource("spark-events/app-1")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anAdministratorReadsAnything() {
        when(access.caller()).thenReturn(ADMIN);

        assertThatCode(() -> drafts.require(source("path: /data/x.csv"))).doesNotThrowAnyException();
        assertThatCode(() -> drafts.requireSource("http://clickhouse:8123/")).doesNotThrowAnyException();
    }
}
