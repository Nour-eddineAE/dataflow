package com.dataflow.workspace.folders;

import java.util.regex.Pattern;

final class FilePaths {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._ -]{1,128}");
    private static final int MAX_LENGTH = 512;

    private FilePaths() {}

    static String check(String path) {
        if (path == null
                || path.isBlank()
                || path.length() > MAX_LENGTH
                || path.startsWith("/")
                || path.endsWith("/")) {
            throw new IllegalArgumentException("Not a file path inside the folder: '" + path + "'");
        }
        for (String segment : path.split("/")) {
            if (!SEGMENT.matcher(segment).matches()
                    || segment.equals(".")
                    || segment.equals("..")
                    || segment.equals(".git")) {
                throw new IllegalArgumentException("Not a file path inside the folder: '" + path + "'");
            }
        }
        return path;
    }
}
