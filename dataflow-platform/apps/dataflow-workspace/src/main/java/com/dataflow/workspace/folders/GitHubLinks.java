package com.dataflow.workspace.folders;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class GitHubLinks {

    public static final String CALLBACK_PATH = "/git/github/callback";
    public static final String CREATED_PATH = "/git/github/app/created";
    public static final String INSTALLED_PATH = "/git/github/installed";
    private static final String API = "/api";
    public static final String CALLBACK = API + CALLBACK_PATH;
    public static final String CREATED = API + CREATED_PATH;
    public static final String INSTALLED = API + INSTALLED_PATH;
    private static final Duration PENDING = Duration.ofMinutes(10);
    private static final int STATE_BYTES = 32;
    private static final int SUFFIX_LENGTH = 6;
    private static final String LETTERS = "abcdefghijkmnpqrstuvwxyz23456789";

    private final GitHub github;
    private final GitCredentials credentials;
    private final SignInStates states;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public GitHubLinks(GitHub github, GitCredentials credentials, SignInStates states, Clock clock) {
        this.github = github;
        this.credentials = credentials;
        this.states = states;
        this.clock = clock;
    }

    public Settings settings() {
        Optional<GitHub.Client> client = github.client();
        return new Settings(
                client.isPresent(),
                github.settings().host(),
                client.map(GitHub.Client::slug)
                        .filter(slug -> !slug.isBlank())
                        .map(slug -> github.install(slug).toString())
                        .orElse(null));
    }

    public Next start(Person person, String requestBase) {
        String base = base(requestBase);
        if (github.client().isEmpty()) {
            String state = remember(person, Step.CREATE_APP, base);
            return new CreateApp(github.newApp(state), github.manifest(base, "DataFlow " + suffix()));
        }
        String redirectUri = base + CALLBACK;
        return new Redirect(github.authorize(remember(person, Step.AUTHORIZE, redirectUri), redirectUri));
    }

    public URI created(Person person, String state, String code) {
        take(person, state, Step.CREATE_APP);
        return github.install(github.convert(code, person, clock.instant()).slug());
    }

    public URI installed(Person person, String requestBase) {
        if (!(start(person, requestBase) instanceof Redirect(URI v))) {
            throw new IllegalArgumentException("The GitHub App is missing; link GitHub again");
        }
        return v;
    }

    public Linked finish(Person person, String state, String code) {
        SignInState entry = take(person, state, Step.AUTHORIZE);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("GitHub sent no code");
        }
        GitHub.Token token = github.exchange(code, entry.redirectUri());
        GitCredential credential = credentials.link(github.login(token.accessToken()), token, person);
        return new Linked(credential, installFirst(token.accessToken()));
    }

    private URI installFirst(String accessToken) {
        String slug = github.client().map(GitHub.Client::slug).orElse(null);
        if (slug == null || slug.isBlank()) {
            return null;
        }
        try {
            return github.installations(accessToken) == 0 ? github.install(slug) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String remember(Person person, Step step, String redirectUri) {
        Instant now = clock.instant();
        byte[] bytes = new byte[STATE_BYTES];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.save(new SignInState(state, person.id(), step.name(), redirectUri, now.plus(PENDING)), now);
        return state;
    }

    private SignInState take(Person person, String state, Step step) {
        SignInState entry = state == null ? null : states.take(state).orElse(null);
        if (entry == null
                || !entry.step().equals(step.name())
                || entry.expiresAt().isBefore(clock.instant())
                || !entry.person().equals(person.id())) {
            throw new IllegalArgumentException("This GitHub sign-in expired or did not start here; try again");
        }
        return entry;
    }

    private String suffix() {
        StringBuilder suffix = new StringBuilder();
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return suffix.toString();
    }

    private String base(String requestBase) {
        String configured = github.settings().publicUrl();
        String base = configured == null || configured.isBlank() ? requestBase : configured;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private enum Step {
        CREATE_APP,
        AUTHORIZE
    }

    public sealed interface Next permits Redirect, CreateApp {}

    public record Redirect(URI to) implements Next {}

    public record CreateApp(URI action, String manifest) implements Next {}

    public record Linked(GitCredential credential, URI install) {}

    public record Settings(boolean ready, String host, String installUrl) {}
}
