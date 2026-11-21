package com.dataflow.workspace.access;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.catalog.Catalog;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.specs.SpecStore;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Query;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.query;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.table;

@Slf4j
@Component
public class ClickHouseRoles {

    public static final String PEOPLE = "dataflow_people";
    static final String PREFIX = "df_";

    private final ClickHouse serving;
    private final SpecStore specs;
    private final Access access;
    private volatile Set<String> roles = Set.of();

    public ClickHouseRoles(ClickHouse serving, SpecStore specs, Access access) {
        this.serving = serving;
        this.specs = specs;
        this.access = access;
    }

    static Map<String, Set<String>> wanted(List<Grant> grants, List<PipelineSpec> specs, String servingDatabase) {
        Map<String, PipelineSpec> byDataset = new LinkedHashMap<>();
        specs.forEach(spec -> byDataset.put(spec.dataset(), spec));
        Map<String, Set<String>> wanted = new LinkedHashMap<>();
        for (Grant grant : grants) {
            if (grant.privilege() != Privilege.SELECT && grant.privilege() != Privilege.MANAGE
                    || grant.securable().kind() == Securable.Kind.FOLDER
                    || grant.securable().kind() == Securable.Kind.SERVICE) {
                continue;
            }
            PipelineSpec spec = byDataset.get(grant.securable().dataset());
            if (spec == null) {
                continue;
            }
            Set<String> objects = wanted.computeIfAbsent(role(grant.principal()), key -> new LinkedHashSet<>());
            String database = Catalog.database(spec.dataset());
            if (grant.securable().kind() == Securable.Kind.DATASET) {
                objects.add(database + ".*");
                spec.serve().forEach(serve -> objects.add(servingDatabase + "." + serve.table()));
            } else {
                String id = grant.securable().name().substring(spec.dataset().length() + 1);
                Catalog.table(spec, id)
                        .filter(table -> table.sqlName() != null)
                        .ifPresent(table -> objects.add(table.sqlName()));
                spec.serve().stream()
                        .filter(serve -> id.equals(serve.from()))
                        .forEach(serve -> objects.add(servingDatabase + "." + serve.table()));
            }
        }
        return wanted;
    }

    static String role(Principal principal) {
        String base = principal.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return PREFIX + (principal.kind() == Principal.Kind.USER ? "u_" : "") + base;
    }

    public ClickHouse.As as(Caller caller) {
        List<String> active = new ArrayList<>();
        caller.groups().stream()
                .map(group -> role(Principal.group(group)))
                .filter(roles::contains)
                .forEach(active::add);
        String own = role(Principal.user(caller.id()));
        if (roles.contains(own)) {
            active.add(own);
        }
        return new ClickHouse.As(PEOPLE, password(), active, ClickHouse.editorComment(caller.id()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        tryApply();
    }

    @EventListener
    public void onChange(Access.Changed changed) {
        tryApply();
    }

    @Scheduled(
            fixedDelayString = WorkspaceProperties.SYNC_EVERY,
            initialDelayString = WorkspaceProperties.SYNC_EVERY)
    public void tryApply() {
        try {
            apply();
        } catch (RuntimeException e) {
            log.warn("ClickHouse roles not applied: {}", e.getMessage());
        }
    }

    public synchronized void apply() {
        Map<String, Set<String>> wanted = wanted(access.grants(), specs.valid(), serving.database());
        List<Query> statements = new ArrayList<>();
        statements.add(query(
                "CREATE USER IF NOT EXISTS {0} IDENTIFIED WITH sha256_password BY {1} DEFAULT ROLE NONE",
                name(PEOPLE), inline(password())));
        statements.add(
                query("ALTER USER {0} IDENTIFIED WITH sha256_password BY {1}", name(PEOPLE), inline(password())));
        Set<String> existing = new LinkedHashSet<>(serving.fetchValues(
                select(field(name("name"), String.class))
                        .from(table(name("system", "roles")))
                        .where(field(name("name"), String.class).startsWith(PREFIX)),
                String.class));
        existing.stream()
                .filter(role -> !wanted.containsKey(role))
                .forEach(role -> statements.add(query("DROP ROLE IF EXISTS {0}", name(role))));
        wanted.forEach((role, objects) -> {
            statements.add(query("CREATE ROLE IF NOT EXISTS {0}", name(role)));
            statements.add(query("REVOKE ALL ON *.* FROM {0}", name(role)));
            for (String object : objects) {
                int dot = object.indexOf('.');
                String database = object.substring(0, dot);
                String table = object.substring(dot + 1);
                statements.add(
                        table.equals("*")
                                ? query("GRANT SELECT ON {0}.* TO {1}", name(database), name(role))
                                : query("GRANT SELECT ON {0} TO {1}", name(database, table), name(role)));
            }
            statements.add(query("GRANT {0} TO {1}", name(role), name(PEOPLE)));
        });
        statements.forEach(serving::execute);
        roles = Set.copyOf(wanted.keySet());
        log.info("ClickHouse roles applied: {}", wanted.keySet());
    }

    private String password() {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(serving.password().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(PEOPLE.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
