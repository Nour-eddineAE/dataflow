package com.dataflow.workspace.security;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.folders.People;
import com.dataflow.workspace.folders.Person;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SignInTest {

    private static final String ISSUER = "http://localhost:8180/realms/dataflow";
    private static final String SAVED_REQUEST = "SPRING_SECURITY_SAVED_REQUEST";
    private static final WorkspaceProperties.Auth AUTH = new WorkspaceProperties.Auth(
            WorkspaceProperties.AuthMode.OIDC,
            ISSUER + "/",
            "http://keycloak:8080/realms/dataflow",
            "workspace",
            "",
            "groups",
            "preferred_username",
            List.of("admins"));

    private static WebApplicationContextRunner security(JwtDecoder decoder) {
        return new WebApplicationContextRunner()
                .withUserConfiguration(SecurityConfiguration.class, SecurityWeb.class)
                .withBean(WorkspaceProperties.class, () -> withAuth(AUTH))
                .withBean("testJwtDecoder", JwtDecoder.class, () -> decoder, bean -> bean.setPrimary(true));
    }

    private static MockMvc mvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    private static Jwt jwt(String audience, List<String> groups) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .audience(List.of(audience))
                .claim("azp", audience)
                .claim("preferred_username", "ada")
                .claim("name", "Ada Lovelace")
                .claim("email", "ada@example.org")
                .claim("groups", groups)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("1")
                .build();
    }

    private static WorkspaceProperties withAuth(WorkspaceProperties.Auth auth) {
        WorkspaceProperties base = Workspace.properties();
        return new WorkspaceProperties(
                base.person(),
                auth,
                base.git(),
                base.releases(),
                base.serving(),
                base.tower(),
                base.catalog(),
                base.links(),
                base.alerts(),
                base.dashboards());
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theBrowserGoesToThePublicAddressAndTheWorkspaceToTheInternalOne() {
        ClientRegistration registration = SecurityConfiguration.registration(AUTH);

        assertThat(registration.getProviderDetails().getAuthorizationUri())
                .isEqualTo(ISSUER + "/protocol/openid-connect/auth");
        assertThat(registration.getProviderDetails().getTokenUri())
                .isEqualTo("http://keycloak:8080/realms/dataflow/protocol/openid-connect/token");
        assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo(ISSUER);
        assertThat(registration.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
    }

    @Test
    void aTokenNamesThePersonAndTheirGroups() {
        People people = new People(withAuth(AUTH));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new JwtAuthenticationToken(jwt("workspace", List.of("/admins", "analysts")), List.of()));

        Person person = people.current();

        assertThat(person.id()).isEqualTo("ada");
        assertThat(person.name()).isEqualTo("Ada Lovelace");
        assertThat(people.groups()).containsExactly("admins", "analysts");
        assertThat(people.admin()).isTrue();
    }

    @Test
    void withoutSignIn_everyoneIsTheConfiguredPersonAndAnAdmin() {
        People people = new People(Workspace.properties());

        assertThat(people.current().id()).isEqualTo("test");
        assertThat(people.admin()).isTrue();
    }

    @Test
    void aTokenForAnotherApplicationIsRefused() {
        assertThat(SecurityConfiguration.tokenValidator(AUTH)
                        .validate(jwt("workspace", List.of()))
                        .hasErrors())
                .isFalse();
        assertThat(SecurityConfiguration.tokenValidator(AUTH)
                        .validate(jwt("grafana", List.of()))
                        .hasErrors())
                .isTrue();
    }

    @Test
    void aSessionPostWithAnArbitraryAuthorizationHeaderStillNeedsCsrf() {
        security(mock(JwtDecoder.class)).run(context -> {
            MockMvc mvc = mvc(context);
            MockHttpSession session = new MockHttpSession();
            var signedIn = SecurityContextHolder.createEmptyContext();
            signedIn.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("ada", null, List.of()));
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, signedIn);
            Cookie csrf = mvc.perform(get("/api/security-test").session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ada"))
                    .andReturn()
                    .getResponse()
                    .getCookie("XSRF-TOKEN");
            assertThat(csrf).isNotNull();

            mvc.perform(post("/api/security-test")
                            .session(session)
                            .cookie(csrf)
                            .header("Authorization", "Basic garbage"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/security-test")
                            .session(session)
                            .cookie(csrf)
                            .header("Authorization", "Basic garbage")
                            .header("X-XSRF-TOKEN", csrf.getValue()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ada"));
        });
    }

    @Test
    void anApiCallIsNeverWhereSignInReturns_aPageIs() {
        security(mock(JwtDecoder.class)).run(context -> {
            MockMvc mvc = mvc(context);
            MockHttpSession api = new MockHttpSession();
            mvc.perform(get("/api/security-test").session(api)).andExpect(status().isUnauthorized());
            assertThat(api.getAttribute(SAVED_REQUEST)).isNull();

            MockHttpSession page = new MockHttpSession();
            mvc.perform(get("/runs").session(page).accept(MediaType.TEXT_HTML))
                    .andExpect(status().is3xxRedirection());
            assertThat(((SavedRequest) page.getAttribute(SAVED_REQUEST)).getRedirectUrl())
                    .contains("/runs");
        });
    }

    @Test
    void aBearerPostDoesNotNeedCsrf() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("token")).thenReturn(jwt("workspace", List.of()));

        security(decoder).run(context -> {
            mvc(context)
                    .perform(post("/api/security-test").header("Authorization", "Bearer token"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));
            verify(decoder).decode("token");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(SecurityEndpoint.class)
    static class SecurityWeb {}

    @RestController
    static class SecurityEndpoint {

        @RequestMapping("/api/security-test")
        String current(Principal principal) {
            return principal.getName();
        }
    }
}
