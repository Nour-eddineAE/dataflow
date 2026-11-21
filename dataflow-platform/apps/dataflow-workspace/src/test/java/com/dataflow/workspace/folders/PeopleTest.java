package com.dataflow.workspace.folders;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.config.WorkspaceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeopleTest {

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private static People people() {
        WorkspaceProperties base = Workspace.properties();
        WorkspaceProperties.Auth auth = new WorkspaceProperties.Auth(
                WorkspaceProperties.AuthMode.OIDC,
                "https://login.example/realms/dataflow",
                "http://keycloak:8080/realms/dataflow",
                "workspace",
                "",
                "groups",
                "preferred_username",
                List.of("admins"));
        return new People(new WorkspaceProperties(
                base.person(),
                auth,
                base.git(),
                base.releases(),
                base.serving(),
                base.tower(),
                base.catalog(),
                base.links(),
                base.alerts(),
                base.dashboards()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void signInModeNeverFallsBackToTheLocalPerson() {
        assertThatThrownBy(() -> people().current()).isInstanceOf(AccessDeniedException.class);

        authenticate(Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("name", "No identity")
                .build());
        assertThatThrownBy(() -> people().current()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theSubjectIdentifiesAPersonWithoutAUsername() {
        authenticate(Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("person-42")
                .build());

        assertThat(people().current().id()).isEqualTo("person-42");
    }

    @Test
    void anUnauthenticatedTokenCannotNameAPerson() {
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("token")
                        .header("alg", "RS256")
                        .subject("person-42")
                        .build()));

        assertThatThrownBy(() -> people().current()).isInstanceOf(AccessDeniedException.class);
    }
}
