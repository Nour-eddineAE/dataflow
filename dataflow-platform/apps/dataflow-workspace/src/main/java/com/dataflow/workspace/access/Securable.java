package com.dataflow.workspace.access;

import com.dataflow.spec.Names;
import java.util.Optional;
import java.util.regex.Pattern;

public record Securable(Kind kind, String name) {

    private static final Pattern TABLE = Pattern.compile("[a-z][a-z0-9_]{0,62}\\.[a-z][a-z0-9_.]{0,126}");
    private static final Pattern FOLDER = Pattern.compile("(Shared|Users/[^/]+)/[A-Za-z0-9._-]{1,64}");
    private static final Pattern SERVICE = Pattern.compile("[a-z][a-z-]{0,30}");

    public Securable {
        boolean valid =
                switch (kind) {
                    case DATASET -> Names.DATASET.matcher(name).matches();
                    case TABLE -> TABLE.matcher(name).matches();
                    case FOLDER -> FOLDER.matcher(name).matches();
                    case SERVICE -> SERVICE.matcher(name).matches();
                };
        if (!valid) {
            throw new IllegalArgumentException("Not a " + kind.name().toLowerCase() + ": " + name);
        }
    }

    public static Securable dataset(String dataset) {
        return new Securable(Kind.DATASET, dataset);
    }

    public static Securable table(String dataset, String table) {
        return new Securable(Kind.TABLE, dataset + "." + table);
    }

    public static Securable folder(String path) {
        return new Securable(Kind.FOLDER, path);
    }

    public static Securable service(String id) {
        return new Securable(Kind.SERVICE, id);
    }

    public static Securable parse(String text) {
        int colon = text == null ? -1 : text.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException(
                    "A securable reads dataset:<name>, table:<dataset>.<table>, folder:<path> or service:<id>");
        }
        Kind kind;
        try {
            kind = Kind.valueOf(text.substring(0, colon).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown securable kind in " + text);
        }
        return new Securable(kind, text.substring(colon + 1));
    }

    public Optional<Securable> parent() {
        return kind == Kind.TABLE ? Optional.of(dataset(name.substring(0, name.indexOf('.')))) : Optional.empty();
    }

    public String dataset() {
        return kind == Kind.TABLE ? name.substring(0, name.indexOf('.')) : kind == Kind.DATASET ? name : null;
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + ":" + name;
    }

    public enum Kind {
        DATASET,
        TABLE,
        FOLDER,
        SERVICE
    }
}
