package com.dataflow.workspace.folders;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySignInStates implements SignInStates {

    private final Map<String, SignInState> states = new ConcurrentHashMap<>();

    @Override
    public void save(SignInState state, Instant now) {
        states.values().removeIf(entry -> entry.expiresAt().isBefore(now));
        states.put(state.state(), state);
    }

    @Override
    public Optional<SignInState> take(String state) {
        return Optional.ofNullable(states.remove(state));
    }
}
