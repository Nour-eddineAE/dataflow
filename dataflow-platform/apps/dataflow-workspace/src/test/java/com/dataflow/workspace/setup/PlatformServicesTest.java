package com.dataflow.workspace.setup;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.InMemoryAccessStore;
import com.dataflow.workspace.access.Principal;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.alerts.Alerts;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.folders.People;
import com.dataflow.workspace.setup.PlatformServices.Health;
import com.dataflow.workspace.setup.PlatformServices.PlatformService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformServicesTest {

    private static final WorkspaceProperties.Auth SIGN_IN = new WorkspaceProperties.Auth(
            WorkspaceProperties.AuthMode.OIDC,
            "http://login.test/realms/dataflow",
            "http://login.test/realms/dataflow",
            "workspace",
            "",
            "groups",
            "preferred_username",
            List.of("admins"));

    private final SetupChecks checks = mock(SetupChecks.class);
    private final Alerts alerts = mock(Alerts.class);
    private Access access;
    private PlatformServices services;

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

    private static WorkspaceProperties properties(WorkspaceProperties.Links links) {
        WorkspaceProperties base = Workspace.properties();
        return new WorkspaceProperties(
                base.person(),
                SIGN_IN,
                base.git(),
                base.releases(),
                base.serving(),
                base.tower(),
                base.catalog(),
                links,
                base.alerts(),
                base.dashboards());
    }

    @BeforeEach
    void setUp() {
        WorkspaceProperties properties = properties(Workspace.properties().links());
        access = new Access(
                new InMemoryAccessStore(),
                new People(properties),
                event -> {},
                Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC));
        services = new PlatformServices(checks, alerts, access, properties);
        when(checks.tower()).thenReturn(Check.passed("Tower", "Dagster answers"));
        when(checks.clickHouse()).thenReturn(Check.passed("ClickHouse", "Answers"));
        when(checks.clickHouseVersion()).thenReturn(Optional.of("26.10.2.1"));
        when(checks.lake()).thenReturn(Check.passed("Lake", "ClickHouse lists s3a://lake"));
        when(checks.releases()).thenReturn(Check.failed("Releases", "Cannot write bucket releases: Access Denied"));
        when(checks.signIn()).thenReturn(Check.passed("Sign-in", "Provider"));
        when(alerts.current()).thenReturn(new Alerts.Current(false, "Grafana is not configured", List.of()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorsSeeEveryServiceWithItsActionsAndOnlyTheFailingReason() {
        as("ada", "admins");

        List<PlatformService> visible = services.visible();

        assertThat(visible)
                .extracting(PlatformService::id, PlatformService::health)
                .containsExactly(
                        tuple("tower", Health.HEALTHY),
                        tuple("serving", Health.HEALTHY),
                        tuple("lake", Health.FAILED),
                        tuple("sign-in", Health.HEALTHY),
                        tuple("alerts", Health.UNKNOWN));
        assertThat(visible.get(2).detail()).isEqualTo("Cannot write bucket releases: Access Denied");
        assertThat(visible.getFirst().actions()).containsExactly("reload");
        assertThat(visible).extracting(PlatformService::version).containsExactly(null, "26.10.2.1", null, null, null);
        assertThat(visible.get(1).responseMs()).isNotNull().isNotNegative();
        assertThat(visible.get(4).responseMs()).isNull();
    }

    @Test
    void othersSeeOnlyTheServicesTheyMayMonitorAndActOnlyWhereTheyManage() {
        as("ada", "admins");
        access.grant(Securable.service("tower"), Principal.group("ops"), Privilege.MONITOR);
        access.grant(Securable.service("serving"), Principal.group("ops"), Privilege.MANAGE);

        as("olga", "ops");
        List<PlatformService> visible = services.visible();

        assertThat(visible)
                .extracting(PlatformService::id, PlatformService::manage)
                .containsExactly(tuple("tower", false), tuple("serving", true));
        assertThat(visible.getFirst().actions()).isEmpty();
        assertThatThrownBy(() -> services.requireManage("tower")).isInstanceOf(AccessDeniedException.class);
        services.requireManage("serving");
    }

    @Test
    void theSparkUiIsListedOnlyWhenItsAddressIsSet() {
        as("ada", "admins");
        WorkspaceProperties.Links links = new WorkspaceProperties.Links("", "", "", "", "", "http://localhost:18080");

        List<PlatformService> visible = new PlatformServices(checks, alerts, access, properties(links)).visible();

        assertThat(visible.getLast())
                .extracting(PlatformService::id, PlatformService::health)
                .containsExactly("spark-history", Health.UNKNOWN);
        assertThat(services.visible()).extracting(PlatformService::id).doesNotContain("spark-history");
    }

    @Test
    void servicesTakeOnlyMonitorOrManage() {
        as("ada", "admins");

        assertThatThrownBy(() -> access.grant(Securable.service("tower"), Principal.group("ops"), Privilege.SELECT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> access.grant(Securable.dataset("earthquakes"), Principal.group("ops"), Privilege.MONITOR))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
