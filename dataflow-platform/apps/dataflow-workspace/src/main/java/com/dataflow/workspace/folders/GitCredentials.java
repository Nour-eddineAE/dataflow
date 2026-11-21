package com.dataflow.workspace.folders;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class GitCredentials {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._ -]{1,64}");

    private static final Duration EARLY = Duration.ofMinutes(5);

    private final FolderStore store;
    private final StringEncryptor encryptor;
    private final GitHub github;
    private final Clock clock;

    public GitCredentials(FolderStore store, StringEncryptor encryptor, GitHub github, Clock clock) {
        this.store = store;
        this.encryptor = encryptor;
        this.github = github;
        this.clock = clock;
    }

    private static boolean nameTaken(List<GitCredential> owned, String name) {
        return owned.stream().anyMatch(credential -> credential.name().equals(name));
    }

    public List<GitCredential> list(Person person) {
        return store.credentials(person.id());
    }

    public GitCredential create(String name, String username, String token, Person person) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("A credential name is 1 to 64 letters, digits, spaces, '.', '_' or '-'");
        }
        if (username == null || username.isBlank() || token == null || token.isBlank()) {
            throw new IllegalArgumentException("A Git credential needs a username and a token");
        }
        if (store.credentials(person.id()).stream()
                .anyMatch(credential -> credential.name().equals(name))) {
            throw new IllegalArgumentException("A credential named '" + name + "' already exists");
        }
        GitCredential credential =
                new GitCredential(UUID.randomUUID(), person.id(), name, username.strip(), null, clock.instant());
        store.insertCredential(credential, StoredSecret.of(encryptor.encrypt(token)));
        return credential;
    }

    public GitCredential link(String login, GitHub.Token token, Person person) {
        StoredSecret secret = stored(token);
        List<GitCredential> owned = store.credentials(person.id());
        Optional<GitCredential> linked = owned.stream()
                .filter(credential -> GitHub.PROVIDER.equals(credential.provider())
                        && credential.username().equals(login))
                .findFirst();
        if (linked.isPresent()) {
            store.updateSecret(linked.get().id(), secret);
            return linked.get();
        }
        String name = "GitHub " + login;
        for (int i = 2; nameTaken(owned, name); i++) {
            name = "GitHub " + login + " " + i;
        }
        GitCredential credential =
                new GitCredential(UUID.randomUUID(), person.id(), name, login, GitHub.PROVIDER, clock.instant());
        store.insertCredential(credential, secret);
        return credential;
    }

    public synchronized String token(UUID id) {
        StoredSecret secret =
                store.secret(id).orElseThrow(() -> new GitFolderException("This Git credential was removed"));
        if (secret.expiresAt() == null || clock.instant().plus(EARLY).isBefore(secret.expiresAt())) {
            return encryptor.decrypt(secret.secret());
        }
        if (secret.refreshSecret() == null) {
            throw new GitFolderException("The GitHub link expired; link GitHub again in Settings");
        }
        GitHub.Token renewed = github.refresh(encryptor.decrypt(secret.refreshSecret()));
        store.updateSecret(id, stored(renewed));
        return renewed.accessToken();
    }

    CredentialsProvider provider(GitFolder folder) {
        if (folder.credentialId() == null) {
            return null;
        }
        if (store.secret(folder.credentialId()).isEmpty()) {
            throw new GitFolderException(folder.path() + " uses a Git credential that was removed");
        }
        String username = store.credentials(folder.owner()).stream()
                .filter(credential -> credential.id().equals(folder.credentialId()))
                .map(GitCredential::username)
                .findFirst()
                .orElse("git");
        return new UsernamePasswordCredentialsProvider(username, token(folder.credentialId()));
    }

    private StoredSecret stored(GitHub.Token token) {
        return new StoredSecret(
                encryptor.encrypt(token.accessToken()),
                token.refreshToken() == null ? null : encryptor.encrypt(token.refreshToken()),
                token.expiresIn() == null ? null : clock.instant().plusSeconds(token.expiresIn()));
    }

    public void delete(UUID id, Person person) {
        store.deleteCredential(id, person.id());
    }
}
