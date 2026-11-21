package com.dataflow.workspace.queries;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.InMemoryAccessStore;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.folders.People;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SavedQueriesTest {

    private final Map<UUID, SavedQuery> saved = new LinkedHashMap<>();
    private final SavedQueries queries;

    SavedQueriesTest() {
        WorkspaceProperties base = Workspace.properties();
        WorkspaceProperties properties = new WorkspaceProperties(
                base.person(),
                new WorkspaceProperties.Auth(
                        WorkspaceProperties.AuthMode.OIDC,
                        "http://login.test/realms/dataflow",
                        "http://login.test/realms/dataflow",
                        "workspace",
                        "",
                        "groups",
                        "preferred_username",
                        List.of("admins")),
                base.git(),
                base.releases(),
                base.serving(),
                base.tower(),
                base.catalog(),
                base.links(),
                base.alerts(),
                base.dashboards());
        Access access = new Access(
                new InMemoryAccessStore(),
                new People(properties),
                event -> {},
                Clock.fixed(Instant.parse("2026-10-26T09:00:00Z"), ZoneOffset.UTC));
        queries = new SavedQueries(
                new SavedQueryStore() {
                    @Override
                    public List<SavedQuery> visibleTo(String person) {
                        return new ArrayList<>(saved.values().stream()
                                .filter(query -> query.owner().equals(person) || query.shared())
                                .toList());
                    }

                    @Override
                    public Optional<SavedQuery> find(UUID id) {
                        return Optional.ofNullable(saved.get(id));
                    }

                    @Override
                    public void save(SavedQuery query) {
                        saved.put(query.id(), query);
                    }

                    @Override
                    public void delete(UUID id) {
                        saved.remove(id);
                    }
                },
                access,
                Clock.fixed(Instant.parse("2026-10-26T09:00:00Z"), ZoneOffset.UTC));
    }

    private static void as(String id) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .claim("preferred_username", id)
                .claim("groups", List.of())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sharedQueriesAreSeenByOthersButChangedOnlyByTheirOwner() {
        as("ada");
        SavedQuery shared = queries.save(null, "Late quakes", "SELECT 1", true);
        SavedQuery mine = queries.save(null, "Scratch", "SELECT 2", false);

        as("bob");
        assertThat(queries.list()).extracting(SavedQuery::name).containsExactly("Late quakes");
        assertThat(queries.get(shared.id()).sql()).isEqualTo("SELECT 1");
        assertThatThrownBy(() -> queries.get(mine.id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queries.save(shared.id(), "Mine now", "SELECT 3", true))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> queries.delete(shared.id())).isInstanceOf(AccessDeniedException.class);

        as("ada");
        assertThat(queries.save(shared.id(), "Late quakes", "SELECT 4", false).sql())
                .isEqualTo("SELECT 4");
        queries.delete(mine.id());
        assertThat(queries.list()).extracting(SavedQuery::name).containsExactly("Late quakes");
    }

    @Test
    void aSavedQueryNeedsANameAndSql() {
        as("ada");
        assertThatThrownBy(() -> queries.save(null, " ", "SELECT 1", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> queries.save(null, "Empty", "", false)).isInstanceOf(IllegalArgumentException.class);
    }
}
