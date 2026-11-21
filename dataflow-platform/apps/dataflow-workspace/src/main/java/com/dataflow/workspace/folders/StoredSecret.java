package com.dataflow.workspace.folders;

import java.time.Instant;

public record StoredSecret(String secret, String refreshSecret, Instant expiresAt) {

    public static StoredSecret of(String secret) {
        return new StoredSecret(secret, null, null);
    }
}
