package com.dataflow.workspace.access;

import java.util.regex.Pattern;

public record Principal(Kind kind, String name) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._@-]{1,100}");

    public Principal {
        if (!valid(name)) {
            throw new IllegalArgumentException("Not a principal name: " + name);
        }
    }

    public static boolean valid(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public static Principal user(String id) {
        return new Principal(Kind.USER, id);
    }

    public static Principal group(String name) {
        return new Principal(Kind.GROUP, name);
    }

    public static Principal parse(String text) {
        int colon = text == null ? -1 : text.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("A principal reads group:<name> or user:<id>");
        }
        String kind = text.substring(0, colon);
        return switch (kind) {
            case "group" -> group(text.substring(colon + 1));
            case "user" -> user(text.substring(colon + 1));
            default -> throw new IllegalArgumentException("Unknown principal kind in " + text);
        };
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase() + ":" + name;
    }

    public enum Kind {
        USER,
        GROUP
    }
}
