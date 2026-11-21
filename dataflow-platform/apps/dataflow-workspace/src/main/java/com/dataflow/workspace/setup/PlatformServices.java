package com.dataflow.workspace.setup;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.alerts.Alerts;
import com.dataflow.workspace.config.WorkspaceProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class PlatformServices {

    public static final String TOWER = "tower";
    public static final String SERVING = "serving";
    public static final String LAKE = "lake";
    public static final String SIGN_IN = "sign-in";
    public static final String ALERTS = "alerts";
    public static final String MAIL = "mail";
    public static final String SPARK_HISTORY = "spark-history";
    public static final List<String> IDS = List.of(TOWER, SERVING, LAKE, SIGN_IN, ALERTS, MAIL, SPARK_HISTORY);
    private static final String ALERTS_NAME = "Alerts";

    private final SetupChecks checks;
    private final Alerts alerts;
    private final Access access;
    private final WorkspaceProperties properties;

    public PlatformServices(SetupChecks checks, Alerts alerts, Access access, WorkspaceProperties properties) {
        this.checks = checks;
        this.alerts = alerts;
        this.access = access;
        this.properties = properties;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public List<PlatformService> visible() {
        Caller caller = access.caller();
        WorkspaceProperties.Links links = properties.links();
        List<Watched> watched = new ArrayList<>(List.of(
                new Watched(TOWER, "Tower", "Dagster", links.tower(), List.of("reload"),
                        () -> List.of(checks.tower()), checks::towerVersion),
                new Watched(SERVING, "Serving", "ClickHouse", links.serving(), List.of("sync"),
                        () -> List.of(checks.clickHouse()), checks::clickHouseVersion),
                new Watched(LAKE, "Lake", store(), links.objectStore(), List.of(),
                        () -> List.of(checks.lake(), checks.releases()), Optional::empty),
                new Watched(SIGN_IN, "Sign-in", properties.auth().enabled() ? "OIDC provider" : "Off", signInLink(),
                        List.of(), () -> List.of(checks.signIn()), Optional::empty),
                new Watched(ALERTS, ALERTS_NAME, "Grafana", links.dashboards(), List.of(), this::alerting, Optional::empty)));
        if (!blank(links.mail())) {
            watched.add(new Watched(MAIL, "Mail", "Mailpit", links.mail(), List.of(), List::of, Optional::empty));
        }
        if (!blank(links.sparkHistory())) {
            watched.add(new Watched(SPARK_HISTORY, "Spark UI", "Spark History Server", links.sparkHistory(), List.of(),
                    List::of, Optional::empty));
        }
        return watched.stream()
                .filter(service -> access.can(caller, Privilege.MONITOR, Securable.service(service.id())))
                .map(service -> state(caller, service))
                .toList();
    }

    public void requireManage(String id) {
        if (!IDS.contains(id)) {
            throw new IllegalArgumentException("Unknown service " + id);
        }
        access.require(Privilege.MANAGE, Securable.service(id));
    }

    private PlatformService state(Caller caller, Watched service) {
        long started = System.nanoTime();
        List<Check> results = service.health().get();
        String version = service.version().get().orElse(null);
        long responseMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        Health state = results.isEmpty()
                ? Health.UNKNOWN
                : results.stream().allMatch(Check::ok) ? Health.HEALTHY : Health.FAILED;
        String detail = results.isEmpty()
                ? "Not checked"
                : String.join(
                        " · ",
                        results.stream()
                                .filter(check -> state == Health.HEALTHY || !check.ok())
                                .map(Check::detail)
                                .toList());
        boolean manage = access.can(caller, Privilege.MANAGE, Securable.service(service.id()));
        return new PlatformService(
                service.id(), service.name(), service.runsOn(), state, detail, version, results.isEmpty() ? null : responseMs,
                blank(service.link()) ? null : service.link(), manage ? service.actions() : List.of(), manage);
    }

    private List<Check> alerting() {
        if (!properties.alerts().enabled()) {
            return List.of();
        }
        Alerts.Current current = alerts.current();
        return List.of(
                current.available()
                        ? Check.passed(
                                ALERTS_NAME, "Grafana answers; " + current.alerts().size() + " firing")
                        : Check.failed(ALERTS_NAME, current.reason()));
    }

    private String store() {
        String root = properties.serving().lakeRoot();
        String scheme = URI.create(root).getScheme();
        return switch (scheme == null ? "" : scheme) {
            case "abfss" -> "Azure Data Lake Storage";
            case "gs" -> "Google Cloud Storage";
            default ->
                blank(properties.releases().endpoint())
                        ? "Amazon S3"
                        : "S3-compatible ("
                                + URI.create(properties.releases().endpoint()).getHost() + ")";
        };
    }

    private String signInLink() {
        String issuer = properties.auth().issuer();
        return properties.auth().enabled() && !blank(issuer) ? issuer + "/account" : null;
    }

    public enum Health {
        HEALTHY,
        FAILED,
        UNKNOWN
    }

    public record PlatformService(
            String id,
            String name,
            String runsOn,
            Health health,
            String detail,
            String version,
            Long responseMs,
            String link,
            List<String> actions,
            boolean manage) {}

    private record Watched(
            String id,
            String name,
            String runsOn,
            String link,
            List<String> actions,
            Supplier<List<Check>> health,
            Supplier<Optional<String>> version) {}
}
