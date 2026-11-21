package com.dataflow.workspace.access;

import com.dataflow.workspace.releases.Released;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TeamOwnership {

    private static final Logger log = LoggerFactory.getLogger(TeamOwnership.class);
    private static final String BY = "release:";

    private final Access access;

    public TeamOwnership(Access access) {
        this.access = access;
    }

    @EventListener
    public void onRelease(Released released) {
        released.owners().forEach((dataset, team) -> {
            if (!Principal.valid(team)) {
                log.warn("Pipeline {} names owner '{}', which is not a group name; no grant made", dataset, team);
                return;
            }
            if (access.ensure(Securable.dataset(dataset), Principal.group(team), Privilege.MANAGE, BY + released.project())) {
                log.info("Team {} now manages {} (release of {})", team, dataset, released.project());
            }
        });
    }
}
