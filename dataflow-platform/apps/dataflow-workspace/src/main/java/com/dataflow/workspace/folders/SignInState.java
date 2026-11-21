package com.dataflow.workspace.folders;

import java.time.Instant;

public record SignInState(String state, String person, String step, String redirectUri, Instant expiresAt) {}
