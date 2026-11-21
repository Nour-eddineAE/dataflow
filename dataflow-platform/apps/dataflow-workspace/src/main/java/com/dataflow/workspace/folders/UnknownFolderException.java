package com.dataflow.workspace.folders;

import java.util.UUID;

public class UnknownFolderException extends RuntimeException {

    public UnknownFolderException(UUID id) {
        super("No Git folder " + id);
    }
}
