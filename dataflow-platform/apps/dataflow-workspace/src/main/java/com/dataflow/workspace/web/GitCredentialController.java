package com.dataflow.workspace.web;

import com.dataflow.workspace.folders.GitCredential;
import com.dataflow.workspace.folders.GitCredentials;
import com.dataflow.workspace.folders.GitHubLinks;
import com.dataflow.workspace.folders.People;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
public class GitCredentialController {

    private static final String GITHUB_ERROR = "github-error";

    private final GitCredentials credentials;
    private final GitHubLinks links;
    private final People people;

    public GitCredentialController(GitCredentials credentials, GitHubLinks links, People people) {
        this.credentials = credentials;
        this.links = links;
        this.people = people;
    }

    private static ResponseEntity<String> redirect(URI to) {
        return ResponseEntity.status(HttpStatus.FOUND).location(to).build();
    }

    private static ResponseEntity<String> settings(String key, String value) {
        return redirect(URI.create(
                "/admin?" + key + "=" + UriUtils.encode(value == null ? "failed" : value, StandardCharsets.UTF_8)));
    }

    private static String autoPost(URI action, String manifest) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>GitHub</title></head>"
                + "<body onload=\"document.forms[0].submit()\"><form method=\"post\" action=\""
                + HtmlUtils.htmlEscape(action.toString()) + "\"><input type=\"hidden\" name=\"manifest\" value=\""
                + HtmlUtils.htmlEscape(manifest)
                + "\"><noscript><button type=\"submit\">Continue to GitHub</button></noscript>"
                + "</form></body></html>";
    }

    @GetMapping("/git/credentials")
    public List<GitCredential> credentials() {
        return credentials.list(people.current());
    }

    @PostMapping("/git/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    public GitCredential createCredential(@RequestBody NewCredential body) {
        return credentials.create(body.name(), body.username(), body.token(), people.current());
    }

    @GetMapping("/git/github")
    public GitHubLinks.Settings github() {
        return links.settings();
    }

    @GetMapping("/git/github/link")
    public ResponseEntity<String> linkGitHub() {
        GitHubLinks.Next next = links.start(
                people.current(),
                ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
        if (next instanceof GitHubLinks.CreateApp(URI action, String manifest)) {
            log.info("GitHub sign-in: no app yet, sending the manifest to {}", action.getHost());
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(autoPost(action, manifest));
        }
        return redirect(((GitHubLinks.Redirect) next).to());
    }

    @GetMapping(GitHubLinks.CREATED_PATH)
    public ResponseEntity<String> gitHubAppCreated(
            @RequestParam(required = false) String code, @RequestParam(required = false) String state) {
        try {
            URI install = links.created(people.current(), state, code);
            log.info("GitHub sign-in: app created, sending to {}", install);
            return redirect(install);
        } catch (RuntimeException e) {
            log.warn("GitHub sign-in: app not created: {}", e.getMessage());
            return settings(GITHUB_ERROR, e.getMessage());
        }
    }

    @GetMapping(GitHubLinks.INSTALLED_PATH)
    public ResponseEntity<String> gitHubInstalled() {
        try {
            log.info("GitHub sign-in: app installed, authorizing");
            return redirect(links.installed(
                    people.current(),
                    ServletUriComponentsBuilder.fromCurrentContextPath().toUriString()));
        } catch (RuntimeException e) {
            log.warn("GitHub sign-in: cannot authorize after install: {}", e.getMessage());
            return settings(GITHUB_ERROR, e.getMessage());
        }
    }

    @GetMapping(GitHubLinks.CALLBACK_PATH)
    public ResponseEntity<String> gitHubCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        try {
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
            GitHubLinks.Linked linked = links.finish(people.current(), state, code);
            log.info(
                    "GitHub sign-in: linked {}{}",
                    linked.credential().name(),
                    linked.install() != null ? ", not installed yet" : "");
            return linked.install() != null
                    ? redirect(linked.install())
                    : settings("github", linked.credential().name());
        } catch (RuntimeException e) {
            log.warn("GitHub sign-in: not linked: {}", e.getMessage());
            return settings(GITHUB_ERROR, e.getMessage());
        }
    }

    @DeleteMapping("/git/credentials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCredential(@PathVariable UUID id) {
        credentials.delete(id, people.current());
    }

    public record NewCredential(String name, String username, String token) {}
}
