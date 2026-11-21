package com.dataflow.workspace.web;

import com.dataflow.workspace.favorites.Favorite;
import com.dataflow.workspace.favorites.Favorites;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final Favorites favorites;

    public FavoriteController(Favorites favorites) {
        this.favorites = favorites;
    }

    private static Favorite.Kind kind(String kind) {
        return Favorite.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
    }

    @GetMapping
    public List<Favorite> list() {
        return favorites.list();
    }

    @PutMapping("/{kind}/{ref}")
    public Favorite add(@PathVariable String kind, @PathVariable String ref) {
        return favorites.add(kind(kind), ref);
    }

    @DeleteMapping("/{kind}/{ref}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String kind, @PathVariable String ref) {
        favorites.remove(kind(kind), ref);
    }
}
