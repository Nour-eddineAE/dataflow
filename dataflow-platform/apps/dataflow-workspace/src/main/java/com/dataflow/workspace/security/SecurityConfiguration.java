package com.dataflow.workspace.security;

import com.dataflow.workspace.config.WorkspaceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    public static final String REGISTRATION = "keycloak";
    private static final String OIDC = "/protocol/openid-connect";

    static OidcUserService oidcUserService(RestOperations http) {
        DefaultOAuth2UserService users = new DefaultOAuth2UserService();
        users.setRestOperations(http);
        OidcUserService oidc = new OidcUserService();
        oidc.setOauth2UserService(users);
        return oidc;
    }

    static RestClientAuthorizationCodeTokenResponseClient tokenClient(RestClient.Builder http) {
        RestClientAuthorizationCodeTokenResponseClient tokens = new RestClientAuthorizationCodeTokenResponseClient();
        tokens.setRestClient(http.configureMessageConverters(converters -> {
                    converters.addCustomConverter(new FormHttpMessageConverter());
                    converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new DefaultResponseErrorHandler())
                .build());
        return tokens;
    }

    static OAuth2TokenValidator<Jwt> tokenValidator(WorkspaceProperties.Auth auth) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(strip(auth.issuer())),
                jwt -> jwt.getAudience() != null && jwt.getAudience().contains(auth.clientId())
                                || auth.clientId().equals(jwt.getClaimAsString("azp"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token", "The token is not meant for " + auth.clientId(), null)));
    }

    static ClientRegistration registration(WorkspaceProperties.Auth auth) {
        String issuer = strip(auth.issuer());
        String internal = strip(auth.internal());
        boolean confidential =
                auth.clientSecret() != null && !auth.clientSecret().isBlank();
        return ClientRegistration.withRegistrationId(REGISTRATION)
                .clientId(auth.clientId())
                .clientSecret(confidential ? auth.clientSecret() : null)
                .clientAuthenticationMethod(
                        confidential ? ClientAuthenticationMethod.CLIENT_SECRET_BASIC : ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(issuer + OIDC + "/auth")
                .tokenUri(internal + OIDC + "/token")
                .jwkSetUri(internal + OIDC + "/certs")
                .userInfoUri(internal + OIDC + "/userinfo")
                .userNameAttributeName(auth.usernameClaim())
                .issuerUri(issuer)
                .providerConfigurationMetadata(Map.of("end_session_endpoint", issuer + OIDC + "/logout"))
                .clientName("DataFlow")
                .build();
    }

    private static boolean api(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    private static String strip(String url) {
        return url == null ? "" : url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Bean
    public SecurityFilterChain workspaceSecurity(HttpSecurity http, WorkspaceProperties properties) throws Exception {
        WorkspaceProperties.Auth auth = properties.auth();
        if (!auth.enabled()) {
            return http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }
        ClientRegistrationRepository registrations = new InMemoryClientRegistrationRepository(registration(auth));
        OidcClientInitiatedLogoutSuccessHandler logout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logout.setPostLogoutRedirectUri("{baseUrl}/");
        HttpSessionRequestCache pages = new HttpSessionRequestCache();
        pages.setRequestMatcher(request -> HttpMethod.GET.matches(request.getMethod()) && !api(request));
        return http.authorizeHttpRequests(
                        requests -> requests.requestMatchers("/assets/**", "/favicon.svg", "/error", "/actuator/health")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2Login(login -> login.clientRegistrationRepository(registrations)
                        .tokenEndpoint(token -> token.accessTokenResponseClient(tokenClient(RestClient.builder())))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService(new RestTemplate())))
                        .loginPage("/oauth2/authorization/" + REGISTRATION))
                .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()))
                .requestCache(cache -> cache.requestCache(pages))
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), SecurityConfiguration::api))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookie(), BasicAuthenticationFilter.class)
                .logout(out -> out.logoutSuccessHandler(logout))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(WorkspaceProperties properties) {
        WorkspaceProperties.Auth auth = properties.auth();
        if (!auth.enabled()) {
            return token -> {
                throw new BadJwtException("Sign-in is off");
            };
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(strip(auth.internal()) + OIDC + "/certs")
                .build();
        decoder.setJwtValidator(tokenValidator(auth));
        return decoder;
    }

    private static final class CsrfCookie extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
