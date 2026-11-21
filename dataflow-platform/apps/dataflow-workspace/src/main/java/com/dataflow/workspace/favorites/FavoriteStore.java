package com.dataflow.workspace.favorites;

import java.util.List;

public interface FavoriteStore {

    List<Favorite> of(String owner);

    void add(String owner, Favorite favorite);

    void remove(String owner, Favorite.Kind kind, String ref);
}
