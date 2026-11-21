package com.dataflow.workspace.housekeeping;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("dataflow.workspace.housekeeping")
@Validated
public record HousekeepingProperties(
        @Min(1) int previewDays,
        @Min(1) int eventLogDays,
        @Min(1) int uploadDays,
        @Min(1) int keepReleases,
        @NotBlank String schedule) {

    static final String SCHEDULE = "${dataflow.workspace.housekeeping.schedule}";
}
