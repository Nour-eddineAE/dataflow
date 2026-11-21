package com.dataflow.workspace.access;

import com.dataflow.workspace.folders.People;
import com.dataflow.workspace.folders.Person;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class Access {

    private static final String GRANT = "GRANT";

    private final AccessStore store;
    private final People people;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final AtomicReference<Grants> grants = new AtomicReference<>();

    public Access(AccessStore store, People people, ApplicationEventPublisher events, Clock clock) {
        this.store = store;
        this.people = people;
        this.events = events;
        this.clock = clock;
    }

    static boolean implies(Privilege held, Privilege wanted) {
        return held == wanted
                || held == Privilege.MANAGE
                || held == Privilege.EDIT && (wanted == Privilege.RUN || wanted == Privilege.READ)
                || held == Privilege.RUN && wanted == Privilege.READ
                || held == Privilege.SELECT && wanted == Privilege.READ;
    }

    private static void checkPrivilege(Securable securable, Privilege privilege) {
        boolean fits =
                switch (securable.kind()) {
                    case DATASET ->
                        privilege == Privilege.SELECT || privilege == Privilege.RUN || privilege == Privilege.MANAGE;
                    case TABLE -> privilege == Privilege.SELECT;
                    case FOLDER -> privilege != Privilege.SELECT && privilege != Privilege.MONITOR;
                    case SERVICE -> privilege == Privilege.MONITOR || privilege == Privilege.MANAGE;
                };
        if (!fits) {
            throw new IllegalArgumentException(privilege + " does not apply to a "
                    + securable.kind().name().toLowerCase());
        }
    }

    public Caller caller() {
        Person person = people.current();
        return new Caller(person.id(), people.groups(), people.admin());
    }

    public List<Grant> grants() {
        return indexed().all();
    }

    private Grants indexed() {
        Grants known = grants.get();
        if (known == null) {
            known = Grants.of(store.grants());
            grants.set(known);
        }
        return known;
    }

    private Stream<Grant> held(Caller caller) {
        Grants known = indexed();
        return Stream.concat(
                        Stream.of(caller.id()).filter(Principal::valid).map(Principal::user),
                        caller.groups().stream().filter(Principal::valid).map(Principal::group))
                .flatMap(principal -> known.of(principal).stream());
    }

    public boolean can(Caller caller, Privilege privilege, Securable securable) {
        if (caller.admin()) {
            return true;
        }
        if (securable.kind() == Securable.Kind.FOLDER && securable.name().startsWith(Person.USERS_FOLDER)) {
            return securable.name().startsWith(Person.USERS_FOLDER + caller.id() + "/");
        }
        return held(caller)
                .filter(grant -> grant.securable().equals(securable)
                        || securable.parent().filter(grant.securable()::equals).isPresent())
                .anyMatch(grant -> implies(grant.privilege(), privilege));
    }

    public boolean can(Privilege privilege, Securable securable) {
        return can(caller(), privilege, securable);
    }

    public void require(Privilege privilege, Securable securable) {
        Caller caller = caller();
        if (!can(caller, privilege, securable)) {
            throw new AccessDeniedException(caller.id() + " lacks " + privilege + " on " + securable);
        }
    }

    public void requireAdmin() {
        Caller caller = caller();
        if (!caller.admin()) {
            throw new AccessDeniedException(caller.id() + " is not an administrator");
        }
    }

    public boolean sees(Caller caller, String dataset) {
        if (caller.admin()) {
            return true;
        }
        return held(caller).anyMatch(grant -> dataset.equals(grant.securable().dataset()));
    }

    public boolean sees(String dataset) {
        return sees(caller(), dataset);
    }

    public Grant grant(Securable securable, Principal principal, Privilege privilege) {
        checkPrivilege(securable, privilege);
        require(Privilege.MANAGE, securable);
        Grant grant = new Grant(securable, principal, privilege, caller().id(), clock.instant());
        store.add(grant);
        store.audit(new AuditEntry(
                clock.instant(),
                grant.grantedBy(),
                GRANT,
                securable.toString(),
                principal.toString(),
                privilege.name()));
        changed();
        return grant;
    }

    public void revoke(Securable securable, Principal principal, Privilege privilege) {
        require(Privilege.MANAGE, securable);
        if (store.remove(securable, principal, privilege)) {
            store.audit(new AuditEntry(
                    clock.instant(),
                    caller().id(),
                    "REVOKE",
                    securable.toString(),
                    principal.toString(),
                    privilege.name()));
            changed();
        }
    }

    public boolean ensure(Securable securable, Principal principal, Privilege privilege, String grantedBy) {
        checkPrivilege(securable, privilege);
        boolean held = grants().stream()
                .anyMatch(grant -> grant.securable().equals(securable)
                        && grant.principal().equals(principal)
                        && grant.privilege() == privilege);
        if (held) {
            return false;
        }
        store.add(new Grant(securable, principal, privilege, grantedBy, clock.instant()));
        store.audit(new AuditEntry(
                clock.instant(), grantedBy, GRANT, securable.toString(), principal.toString(), privilege.name()));
        changed();
        return true;
    }

    public void owner(Securable securable, String person) {
        store.add(new Grant(securable, Principal.user(person), Privilege.MANAGE, person, clock.instant()));
        store.audit(new AuditEntry(
                clock.instant(),
                person,
                GRANT,
                securable.toString(),
                Principal.user(person).toString(),
                Privilege.MANAGE.name()));
        changed();
    }

    public void record(AuditAction action, Securable securable) {
        store.audit(new AuditEntry(clock.instant(), caller().id(), action.name(), securable.toString(), "", ""));
    }

    public List<AuditEntry> audits(int limit) {
        return store.audits(limit);
    }

    public void reload() {
        grants.set(null);
        events.publishEvent(new Changed(grants()));
    }

    @Scheduled(fixedDelayString = "${dataflow.workspace.access.refresh-every}",
            initialDelayString = "${dataflow.workspace.access.refresh-every}")
    public void refresh() {
        List<Grant> stored = store.grants();
        Grants known = grants.get();
        if (known == null || !known.all().equals(stored)) {
            grants.set(Grants.of(stored));
            events.publishEvent(new Changed(stored));
        }
    }

    private void changed() {
        reload();
    }

    public record Changed(List<Grant> grants) {}

    private record Grants(List<Grant> all, Map<Principal, List<Grant>> byPrincipal) {

        static Grants of(List<Grant> grants) {
            return new Grants(List.copyOf(grants), grants.stream().collect(Collectors.groupingBy(Grant::principal)));
        }

        List<Grant> of(Principal principal) {
            return byPrincipal.getOrDefault(principal, List.of());
        }
    }
}
