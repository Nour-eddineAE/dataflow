package com.dataflow.workspace.security;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.config.WorkspaceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OidcUsersTest {

    private static final String ISSUER = "https://identity.test/realms/dataflow";
    private static final String USERINFO = ISSUER + "/protocol/openid-connect/userinfo";
    private static final WorkspaceProperties.Auth AUTH = new WorkspaceProperties.Auth(
            WorkspaceProperties.AuthMode.OIDC,
            ISSUER,
            ISSUER,
            "workspace",
            "",
            "groups",
            "preferred_username",
            List.of("admins"));

    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(http).build();

    private static OidcUserRequest request() {
        Instant now = Instant.now();
        OAuth2AccessToken access = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                now,
                now.plusSeconds(300),
                Set.of("openid", "profile", "email"));
        OidcIdToken identity = OidcIdToken.withTokenValue("id-token")
                .issuer(ISSUER)
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("preferred_username", "ada")
                .build();
        return new OidcUserRequest(SecurityConfiguration.registration(AUTH), access, identity);
    }

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/security", name));
    }

    @Test
    void userInfoUsesTheAccessTokenAndReturnsTheVerifiedPerson() throws IOException {
        server.expect(requestTo(USERINFO))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andRespond(withSuccess(fixture("userinfo.json"), MediaType.APPLICATION_JSON));

        OidcUser user = SecurityConfiguration.oidcUserService(http).loadUser(request());

        assertThat(user.getSubject()).isEqualTo("1");
        assertThat(user.getName()).isEqualTo("ada");
        assertThat(user.getFullName()).isEqualTo("Ada Lovelace");
        server.verify();
    }

    @Test
    void aProviderJsonErrorFailsAuthentication() throws IOException {
        server.expect(requestTo(USERINFO))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("userinfo-error.json")));

        assertUserInfoRejected();
        server.verify();
    }

    @Test
    void aBearerChallengeFailsAuthenticationWithoutParsingTheNimbusSdk() {
        server.expect(requestTo(USERINFO))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .header(
                                HttpHeaders.WWW_AUTHENTICATE,
                                "Bearer realm=\"dataflow\", error=\"invalid_token\","
                                        + " error_description=\"Expired token\""));

        assertUserInfoRejected();
        server.verify();
    }

    @Test
    void aSuccessfulResponseForAnotherSubjectFailsAuthentication() throws IOException {
        server.expect(requestTo(USERINFO))
                .andRespond(withSuccess(fixture("userinfo-substitution.json"), MediaType.APPLICATION_JSON));

        assertUserInfoRejected();
        server.verify();
    }

    @Test
    void oidcSecurityStartsWithExplicitProviderEndpoints() {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(SecurityConfiguration.class, Settings.class);
            context.refresh();

            assertThat(context.getBean(SecurityFilterChain.class)).isNotNull();
        }
    }

    private void assertUserInfoRejected() {
        assertThatThrownBy(() -> SecurityConfiguration.oidcUserService(http).loadUser(request()))
                .isInstanceOfSatisfying(
                        OAuth2AuthenticationException.class,
                        error -> assertThat(error.getError().getErrorCode()).isEqualTo("invalid_user_info_response"));
    }

    @Configuration(proxyBeanMethods = false)
    static class Settings {

        @Bean
        WorkspaceProperties properties() {
            WorkspaceProperties base = Workspace.properties();
            return new WorkspaceProperties(
                    base.person(),
                    AUTH,
                    base.git(),
                    base.releases(),
                    base.serving(),
                    base.tower(),
                    base.catalog(),
                    base.links(),
                    base.alerts(),
                    base.dashboards());
        }
    }
}
