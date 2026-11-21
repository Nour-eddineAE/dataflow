package com.dataflow.workspace.favorites;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class Favorites {

    private static final Pattern REF = Pattern.compile("[A-Za-z0-9_.-]{1,200}");

    private final FavoriteStore store;
    private final Access access;
    private final Clock clock;

    public Favorites(FavoriteStore store, Access access, Clock clock) {
        this.store = store;
        this.access = access;
        this.clock = clock;
    }

    static String dataset(Favorite favorite) {
        return switch (favorite.kind()) {
            case PIPELINE -> favorite.ref();
            case TABLE -> favorite.ref().substring(0, Math.max(0, favorite.ref().indexOf('.')));
            case SERVED, QUERY -> null;
        };
    }

    public List<Favorite> list() {
        Caller caller = access.caller();
        return store.of(caller.id()).stream()
                .filter(favorite -> dataset(favorite) == null || access.sees(caller, dataset(favorite)))
                .toList();
    }

    public Favorite add(Favorite.Kind kind, String ref) {
        if (ref == null || !REF.matcher(ref).matches()) {
            throw new IllegalArgumentException("Not something to keep as a favorite: " + ref);
        }
        Favorite favorite = new Favorite(kind, ref, clock.instant());
        store.add(access.caller().id(), favorite);
        return favorite;
    }

    public void remove(Favorite.Kind kind, String ref) {
        store.remove(access.caller().id(), kind, ref);
    }
}
