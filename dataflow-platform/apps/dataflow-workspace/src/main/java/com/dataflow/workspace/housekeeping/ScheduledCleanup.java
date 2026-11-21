package com.dataflow.workspace.housekeeping;

import com.dataflow.spec.Names;
import com.dataflow.workspace.housekeeping.Candidate.Kind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Slf4j
@Component
public class ScheduledCleanup {

    private final Housekeeping housekeeping;

    public ScheduledCleanup(Housekeeping housekeeping) {
        this.housekeeping = housekeeping;
    }

    @Scheduled(cron = HousekeepingProperties.SCHEDULE)
    public void clean() {
        try {
            Housekeeping.Applied applied = housekeeping.apply(EnumSet.allOf(Kind.class), Names.PLATFORM);
            log.info(
                    "Scheduled clean up removed {} releases and catalogs and sent {} lake locations to task {}",
                    applied.removed(),
                    applied.lakeLocations(),
                    applied.lakeTask() == null ? "none" : applied.lakeTask().taskId());
        } catch (RuntimeException e) {
            log.warn("Scheduled clean up did not run: {}", e.getMessage());
        }
    }
}
