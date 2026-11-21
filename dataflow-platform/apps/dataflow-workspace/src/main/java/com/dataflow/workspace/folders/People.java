package com.dataflow.workspace.folders;

import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.config.WorkspaceProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class People {

    private final Person configured;
    private final WorkspaceProperties.Auth auth;

    public People(WorkspaceProperties properties) {
        WorkspaceProperties.Person person = properties.person();
        this.configured = new Person(person.id(), person.name(), person.email());
        this.auth = properties.auth();
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    public Person current() {
        if (!auth.enabled()) {
            return configured;
        }
        Map<String, Object> claims = claims();
        if (claims == null) {
            throw new AccessDeniedException("Sign in to continue");
        }
        String id = text(claims.get(auth.usernameClaim()), text(claims.get("sub"), null));
        if (id == null) {
            throw new AccessDeniedException("The sign-in token has no identity");
        }
        String name = text(claims.get("name"), id);
        String email = text(
                claims.get("email"),
                id + "@" + configured.email().substring(configured.email().indexOf('@') + 1));
        return new Person(id, name, email);
    }

    public List<String> groups() {
        Map<String, Object> claims = claims();
        if (claims == null) {
            return auth.enabled() ? List.of() : auth.admins();
        }
        return claims.get(auth.groupsClaim()) instanceof List<?> list
                ? list.stream()
                        .map(String::valueOf)
                        .map(group -> group.startsWith("/") ? group.substring(1) : group)
                        .toList()
                : List.of();
    }

    public boolean admin() {
        return groups().stream().anyMatch(auth.admins()::contains);
    }

    private Map<String, Object> claims() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof OidcUser user) {
            return user.getClaims();
        }
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return jwt.getClaims();
        }
        return null;
    }
}
