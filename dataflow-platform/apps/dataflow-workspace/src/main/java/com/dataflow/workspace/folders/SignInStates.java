package com.dataflow.workspace.folders;

import java.time.Instant;
import java.util.Optional;

public interface SignInStates {

    void save(SignInState state, Instant now);

    Optional<SignInState> take(String state);
}
