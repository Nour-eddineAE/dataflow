package com.dataflow.workspace.security;

import com.dataflow.workspace.config.WorkspaceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OidcTokensTest {

    private static final String ISSUER = "https://identity.test/realms/dataflow";
    private static final String TOKEN = ISSUER + "/protocol/openid-connect/token";
    private static final String REDIRECT = "https://workspace.test/login/oauth2/code/keycloak";
    private static final WorkspaceProperties.Auth AUTH = new WorkspaceProperties.Auth(
            WorkspaceProperties.AuthMode.OIDC,
            ISSUER,
            ISSUER,
            "workspace",
            "",
            "groups",
            "preferred_username",
            List.of("admins"));

    private final RestClient.Builder http = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(http).build();

    private static OAuth2AuthorizationCodeGrantRequest request() {
        ClientRegistration registration = SecurityConfiguration.registration(AUTH);
        OAuth2AuthorizationRequest authorization = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                .clientId(registration.getClientId())
                .redirectUri(REDIRECT)
                .scope("openid", "profile", "email")
                .state("state")
                .build();
        OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success("authorization-code")
                .redirectUri(REDIRECT)
                .state("state")
                .build();
        return new OAuth2AuthorizationCodeGrantRequest(
                registration, new OAuth2AuthorizationExchange(authorization, response));
    }

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/security", name));
    }

    @Test
    void exchangeUsesSpringFormAndTokenConverters() throws IOException {
        server.expect(requestTo(TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content()
                        .formDataContains(Map.of("grant_type", "authorization_code", "code", "authorization-code")))
                .andRespond(withSuccess(fixture("token-response.json"), MediaType.APPLICATION_JSON));

        var response = SecurityConfiguration.tokenClient(http).getTokenResponse(request());

        assertThat(response.getAccessToken().getTokenValue()).isEqualTo("access-token");
        assertThat(response.getAccessToken().getTokenType()).isEqualTo(OAuth2AccessToken.TokenType.BEARER);
        assertThat(response.getAccessToken().getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(response.getRefreshToken().getTokenValue()).isEqualTo("refresh-token");
        assertThat(response.getAdditionalParameters()).containsEntry("id_token", "identity-token");
        server.verify();
    }

    @Test
    void aProviderJsonErrorFailsTokenExchange() throws IOException {
        server.expect(requestTo(TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("token-error.json")));

        assertTokenRejected();
        server.verify();
    }

    @Test
    void aBearerChallengeFailsTokenExchangeWithoutTheNimbusSdk() throws IOException {
        server.expect(requestTo(TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("token-error.json")));

        assertTokenRejected();
        server.verify();
    }

    @Test
    void aSuccessfulResponseWithoutAnAccessTokenIsRejected() throws IOException {
        server.expect(requestTo(TOKEN))
                .andRespond(withSuccess(fixture("token-invalid.json"), MediaType.APPLICATION_JSON));

        assertTokenRejected();
        server.verify();
    }

    private void assertTokenRejected() {
        assertThatThrownBy(() -> SecurityConfiguration.tokenClient(http).getTokenResponse(request()))
                .isInstanceOfSatisfying(
                        OAuth2AuthorizationException.class,
                        error -> assertThat(error.getError().getErrorCode()).isEqualTo("invalid_token_response"));
    }
}
