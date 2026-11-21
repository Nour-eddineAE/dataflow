package com.dataflow.workspace.access;

import com.dataflow.workspace.Drafts;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.folders.People;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTest {

    private static final WorkspaceProperties.Auth SIGN_IN = new WorkspaceProperties.Auth(
            WorkspaceProperties.AuthMode.OIDC,
            "http://login.test/realms/dataflow",
            "http://login.test/realms/dataflow",
            "workspace",
            "",
            "groups",
            "preferred_username",
            List.of("admins"));

    private final InMemoryAccessStore store = new InMemoryAccessStore();
    private final List<Object> events = new ArrayList<>();
    private Access access;

    private static void as(String id, String... groups) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("preferred_username", id)
                .claim("groups", List.of(groups))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @BeforeEach
    void setUp() {
        WorkspaceProperties base = Workspace.properties();
        WorkspaceProperties properties = new WorkspaceProperties(
                base.person(),
                SIGN_IN,
                base.git(),
                base.releases(),
                base.serving(),
                base.tower(),
                base.catalog(),
                base.links(),
                base.alerts(),
                base.dashboards());
        access = new Access(
                store,
                new People(properties),
                events::add,
                Clock.fixed(Instant.parse("2026-10-26T09:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anActionNamesWhoDidItAndOnWhat() {
        as("bob", "analysts");

        access.record(AuditAction.RUN, Securable.dataset("earthquakes"));

        assertThat(store.audits(10)).singleElement().satisfies(entry -> {
            assertThat(entry.actor()).isEqualTo("bob");
            assertThat(entry.action()).isEqualTo("RUN");
            assertThat(entry.securable()).isEqualTo("dataset:earthquakes");
        });
    }

    @Test
    void anAdministratorGrantsAndTheGroupReadsTheWholeDataset() {
        as("ada", "admins");
        access.grant(Securable.dataset("earthquakes"), Principal.group("analysts"), Privilege.SELECT);

        as("bob", "analysts");
        assertThat(access.can(Privilege.SELECT, Securable.table("earthquakes", "current")))
                .isTrue();
        assertThat(access.can(Privilege.SELECT, Securable.table("earthquakes", "gold.regions")))
                .isTrue();
        assertThat(access.can(Privilege.RUN, Securable.dataset("earthquakes"))).isFalse();
        assertThat(access.sees("earthquakes")).isTrue();
        assertThat(access.sees("taxi_trips")).isFalse();
        assertThat(store.audits(10)).singleElement().satisfies(entry -> {
            assertThat(entry.actor()).isEqualTo("ada");
            assertThat(entry.action()).isEqualTo("GRANT");
            assertThat(entry.securable()).isEqualTo("dataset:earthquakes");
        });
    }

    @Test
    void aTableGrantShowsOnlyThatTable() {
        as("ada", "admins");
        access.grant(Securable.table("earthquakes", "gold.regions"), Principal.user("bob"), Privilege.SELECT);

        as("bob");
        assertThat(access.can(Privilege.SELECT, Securable.table("earthquakes", "gold.regions")))
                .isTrue();
        assertThat(access.can(Privilege.SELECT, Securable.table("earthquakes", "current")))
                .isFalse();
        assertThat(access.sees("earthquakes")).isTrue();
    }

    @Test
    void onlyAManagerGrants() {
        as("bob", "analysts");
        assertThatThrownBy(
                        () -> access.grant(Securable.dataset("earthquakes"), Principal.user("bob"), Privilege.SELECT))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("bob lacks MANAGE on dataset:earthquakes");
        assertThat(store.grants()).isEmpty();
    }

    @Test
    void foldersFollowDatabricksLevels() {
        access.owner(Securable.folder("Shared/analytics"), "carol");
        as("carol");
        access.grant(Securable.folder("Shared/analytics"), Principal.group("engineers"), Privilege.EDIT);

        as("dan", "engineers");
        assertThat(access.can(Privilege.READ, Securable.folder("Shared/analytics")))
                .isTrue();
        assertThat(access.can(Privilege.RUN, Securable.folder("Shared/analytics")))
                .isTrue();
        assertThat(access.can(Privilege.MANAGE, Securable.folder("Shared/analytics")))
                .isFalse();
        assertThat(access.can(Privilege.READ, Securable.folder("Users/carol/notes")))
                .isFalse();
        assertThat(access.can(Privilege.EDIT, Securable.folder("Users/dan/mine")))
                .isTrue();
    }

    @Test
    void aPrivilegeMustFitTheSecurable() {
        as("ada", "admins");
        assertThatThrownBy(() -> access.grant(Securable.folder("Shared/x"), Principal.user("bob"), Privilege.SELECT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        access.grant(Securable.table("earthquakes", "current"), Principal.user("bob"), Privilege.RUN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grantsBecomeClickHouseRoles() {
        List<Grant> grants = List.of(
                new Grant(
                        Securable.dataset("earthquakes"),
                        Principal.group("analysts"),
                        Privilege.SELECT,
                        "ada",
                        Instant.EPOCH),
                new Grant(
                        Securable.table("earthquakes", "gold.regions"),
                        Principal.user("bob.smith"),
                        Privilege.SELECT,
                        "ada",
                        Instant.EPOCH),
                new Grant(
                        Securable.dataset("earthquakes"),
                        Principal.group("engineers"),
                        Privilege.RUN,
                        "ada",
                        Instant.EPOCH));

        Map<String, Set<String>> roles = ClickHouseRoles.wanted(grants, List.of(Drafts.spec("earthquakes")), "serving");

        assertThat(roles).containsOnlyKeys("df_analysts", "df_u_bob_smith");
        assertThat(roles.get("df_analysts"))
                .contains("earthquakes.*", "serving.earthquakes", "serving.earthquakes_regions");
        assertThat(roles.get("df_u_bob_smith"))
                .containsExactly("earthquakes.gold_regions", "serving.earthquakes_regions");
    }

    @Test
    void aGrantMadeOnAnotherReplicaIsSeenAtTheNextRefresh() {
        as("ada", "analysts");
        assertThat(access.can(Privilege.SELECT, Securable.dataset("earthquakes"))).isFalse();

        store.add(new Grant(Securable.dataset("earthquakes"), Principal.group("analysts"), Privilege.SELECT, "root",
                Instant.parse("2026-10-26T09:00:00Z")));
        access.refresh();
        access.refresh();

        assertThat(access.can(Privilege.SELECT, Securable.dataset("earthquakes"))).isTrue();
        assertThat(events).hasSize(1);
    }

    @Test
    void aNameNoGrantCanHoldIsSimplyNotMatched() {
        as("ada+test@example.org", "data team");

        assertThat(access.can(Privilege.SELECT, Securable.dataset("earthquakes"))).isFalse();
        assertThat(access.sees("earthquakes")).isFalse();
    }

    @Test
    void aReleaseLetsTheOwningTeamManageItsPipelinesOnce() {
        TeamOwnership ownership = new TeamOwnership(access);

        ownership.onRelease(new com.dataflow.workspace.releases.Released(
                "examples", "prod", "ada", Map.of("earthquakes", "platform", "zones", "not a group")));
        ownership.onRelease(new com.dataflow.workspace.releases.Released(
                "examples", "prod", "ada", Map.of("earthquakes", "platform")));

        as("olga", "platform");
        assertThat(access.can(Privilege.MANAGE, Securable.dataset("earthquakes"))).isTrue();
        assertThat(store.grants()).singleElement().returns("release:examples", Grant::grantedBy);
        assertThat(access.audits(10)).singleElement().returns("GRANT", AuditEntry::action);
    }
}
