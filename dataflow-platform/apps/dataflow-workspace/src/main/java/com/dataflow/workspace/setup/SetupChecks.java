package com.dataflow.workspace.setup;

import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.releases.ReleaseStore;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.selectCount;

@Component
public class SetupChecks {

    private static final String LAKE = "Lake";
    private static final String RELEASES = "Releases";

    static final String RELEASES_PROBE = "system/checks/workspace.txt";

    private final ClickHouse serving;
    private final LakeFunctions lake;
    private final Tower tower;
    private final ReleaseStore releases;
    private final WorkspaceProperties properties;
    private final Clock clock;
    private final SignInCheck signIn;

    public SetupChecks(
            ClickHouse serving,
            LakeFunctions lake,
            Tower tower,
            ReleaseStore releases,
            WorkspaceProperties properties,
            Clock clock,
            SignInCheck signIn) {
        this.serving = serving;
        this.lake = lake;
        this.tower = tower;
        this.releases = releases;
        this.properties = properties;
        this.clock = clock;
        this.signIn = signIn;
    }

    public List<Check> run() {
        Check clickHouse = clickHouse();
        return List.of(
                clickHouse,
                clickHouse.ok() ? lake() : Check.failed(LAKE, "Not checked: ClickHouse reads the lake"),
                releases(),
                tower(),
                signIn());
    }

    public Check clickHouse() {
        String url = properties.serving().url();
        return serving.reachable()
                ? Check.passed("ClickHouse", "Answers at " + url)
                : Check.unreachable("ClickHouse", url, ". Queries, the catalog and dashboards need it.");
    }

    public Check lake() {
        String root = properties.serving().lakeRoot();
        try {
            serving.fetch(selectCount().from(lake.objects("*")), 1);
            return Check.passed(LAKE, "ClickHouse lists " + root);
        } catch (RuntimeException e) {
            return Check.failed(
                    LAKE,
                    "ClickHouse cannot list " + root + " through its named collection '"
                            + properties.serving().collection() + "': " + Check.firstLine(e));
        }
    }

    Check releases() {
        String bucket = properties.releases().bucket();
        byte[] probe = clock.instant().toString().getBytes(StandardCharsets.UTF_8);
        try {
            releases.put(RELEASES_PROBE, probe);
            boolean readBack = releases.get(RELEASES_PROBE)
                    .map(read -> Arrays.equals(read, probe))
                    .orElse(false);
            return readBack
                    ? Check.passed(RELEASES, "Writes and reads bucket " + bucket)
                    : Check.failed(RELEASES, "Bucket " + bucket + " did not return what was written");
        } catch (RuntimeException e) {
            return Check.failed(RELEASES, "Cannot write bucket " + bucket + ": " + Check.firstLine(e));
        }
    }

    public Optional<String> towerVersion() {
        return tower.version();
    }

    public Optional<String> clickHouseVersion() {
        return serving.version();
    }

    public Check tower() {
        String url = properties.tower().url();
        return tower.reachable()
                ? Check.passed("Tower", "Dagster answers at " + url)
                : Check.unreachable("Tower", url, ". Runs, schedules and previews need it.");
    }

    Check signIn() {
        return signIn.run();
    }
}
