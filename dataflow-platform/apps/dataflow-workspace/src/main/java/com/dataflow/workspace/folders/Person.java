package com.dataflow.workspace.folders;

public record Person(String id, String name, String email) {

    public static final String USERS_FOLDER = "Users/";
}
