package com.dataflow.workspace.favorites;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.InMemoryAccessStore;
import com.dataflow.workspace.access.Principal;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FavoritesTest {

    private final Map<String, List<Favorite>> stored = new HashMap<>();
    private final Access access;
    private final Favorites favorites;

    FavoritesTest() {
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
        Clock clock = Clock.fixed(Instant.parse("2026-10-30T15:00:00Z"), ZoneOffset.UTC);
        access = new Access(new InMemoryAccessStore(), new People(properties), event -> {}, clock);
        favorites = new Favorites(new FavoriteStore() {
            @Override
            public List<Favorite> of(String owner) {
                return List.copyOf(stored.getOrDefault(owner, List.of()));
            }

            @Override
            public void add(String owner, Favorite favorite) {
                stored.computeIfAbsent(owner, key -> new ArrayList<>()).add(favorite);
            }

            @Override
            public void remove(String owner, Favorite.Kind kind, String ref) {
                stored.getOrDefault(owner, new ArrayList<>())
                        .removeIf(favorite -> favorite.kind() == kind && favorite.ref().equals(ref));
            }
        }, access, clock);
    }

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

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void favoritesArePersonalAndHideWhatThePersonCanNoLongerRead() {
        as("root", "admins");
        access.grant(Securable.dataset("earthquakes"), Principal.group("analysts"), Privilege.SELECT);

        as("ada", "analysts");
        favorites.add(Favorite.Kind.PIPELINE, "earthquakes");
        favorites.add(Favorite.Kind.TABLE, "earthquakes.current");
        favorites.add(Favorite.Kind.TABLE, "taxi_trips.current");
        favorites.add(Favorite.Kind.SERVED, "zone_hours");

        assertThat(favorites.list())
                .extracting(Favorite::kind, Favorite::ref)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Favorite.Kind.PIPELINE, "earthquakes"),
                        org.assertj.core.groups.Tuple.tuple(Favorite.Kind.TABLE, "earthquakes.current"),
                        org.assertj.core.groups.Tuple.tuple(Favorite.Kind.SERVED, "zone_hours"));

        as("bob", "analysts");
        assertThat(favorites.list()).isEmpty();

        as("ada", "analysts");
        favorites.remove(Favorite.Kind.TABLE, "earthquakes.current");
        assertThat(favorites.list()).extracting(Favorite::ref).containsExactly("earthquakes", "zone_hours");
    }

    @Test
    void onlyNamesAreKept() {
        as("ada", "analysts");

        assertThatThrownBy(() -> favorites.add(Favorite.Kind.TABLE, "../etc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> favorites.add(Favorite.Kind.QUERY, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
