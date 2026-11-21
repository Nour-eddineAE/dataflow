package com.dataflow.workspace.access;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.Drafts;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoragePolicyDocumentTest {

    private static final List<PipelineSpec> SPECS = List.of(Drafts.spec("earthquakes"), Drafts.spec("taxi_trips"));
    private static final Principal ANALYSTS = Principal.group("analysts");
    private static final Caller BOB = new Caller("bob", List.of("analysts"), false);

    private static Grant grant(Securable on, Principal to, Privilege privilege) {
        return new Grant(on, to, privilege, "admin", Instant.EPOCH);
    }

    @Test
    void aDatasetReadGrantProducesOnlyBoundedReadAndListPermissions() {
        List<Grant> grants = List.of(
                grant(Securable.dataset("earthquakes"), ANALYSTS, Privilege.SELECT),
                grant(Securable.dataset("taxi_trips"), ANALYSTS, Privilege.RUN),
                grant(Securable.table("taxi_trips", "current"), ANALYSTS, Privilege.SELECT),
                grant(Securable.folder("Shared/examples"), ANALYSTS, Privilege.MANAGE));
        JsonNode policy = JsonMapper.builder().build().valueToTree(
                StoragePolicyDocument.build(grants, SPECS, List.of("admins"), "lake"));
        JsonNode statements = policy.path("Statement");

        assertThat(statements.size()).isEqualTo(6);
        assertThat(statements.get(0).path("Action").toString()).isEqualTo("[\"admin:*\"]");
        assertThat(statements.get(1).path("Action").toString()).isEqualTo("[\"s3:*\"]");
        assertThat(statements.get(0).path("Condition").path("ForAnyValue:StringEquals").path("jwt:groups").toString())
                .isEqualTo("[\"admins\",\"/admins\"]");
        JsonNode listing = statements.get(4);
        assertThat(listing.path("Condition").path("StringLike").path("s3:prefix").toString())
                .contains("bronze/earthquakes/*", "history/earthquakes/*", "system/events/earthquakes/*")
                .doesNotContain("taxi_trips", "checkpoints", "spark-events");
        assertThat(statements.get(5).path("Resource").toString())
                .contains("arn:aws:s3:::lake/bronze/earthquakes/*")
                .doesNotContain("earthquakes*", "taxi_trips", "releases");
        assertThat(statements.get(5).path("Action").toString())
                .isEqualTo("[\"s3:GetObject\",\"s3:GetObjectVersion\"]");
    }

    @Test
    void individualManageGrantsMatchTheExactUsername() {
        var document = StoragePolicyDocument.build(List.of(
                grant(Securable.dataset("earthquakes"), Principal.user("Alice.Name"), Privilege.MANAGE)),
                SPECS, List.of("admins"), "lake");
        JsonNode policy = JsonMapper.builder().build().valueToTree(document);

        assertThat(policy.path("Statement").get(5).path("Condition").path("StringEquals")
                .path("jwt:preferred_username").toString()).isEqualTo("[\"Alice.Name\"]");
    }

    @Test
    void revokedAndUnknownDatasetsLeaveOnlyTheAdministratorStatement() {
        var document = StoragePolicyDocument.build(List.of(
                grant(Securable.dataset("removed_dataset"), ANALYSTS, Privilege.SELECT)),
                SPECS, List.of("admins"), "lake");

        assertThat(JsonMapper.builder().build().valueToTree(document).path("Statement").size()).isEqualTo(2);
        assertThatThrownBy(() -> StoragePolicyDocument.build(List.of(), SPECS, List.of(), "lake"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storageLinksRequireAWholeDatasetReadGrant() {
        List<Grant> grants = List.of(
                grant(Securable.dataset("earthquakes"), ANALYSTS, Privilege.SELECT),
                grant(Securable.table("taxi_trips", "current"), Principal.user("bob"), Privilege.SELECT));

        assertThat(StoragePolicyDocument.datasets(BOB, grants, SPECS)).containsExactly("earthquakes");
        assertThat(StoragePolicyDocument.datasets(new Caller("other", List.of(), false), grants, SPECS)).isEmpty();
        assertThat(StoragePolicyDocument.datasets(new Caller("admin", List.of(), true), grants, SPECS))
                .containsExactly("earthquakes", "taxi_trips");
        assertThatThrownBy(() -> StoragePolicyDocument.prefixes("../other"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
