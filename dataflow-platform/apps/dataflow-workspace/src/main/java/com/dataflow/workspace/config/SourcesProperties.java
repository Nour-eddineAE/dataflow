package com.dataflow.workspace.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties("dataflow.workspace.sources")
@Validated
public record SourcesProperties(@NotNull List<String> allowedHosts, @NotNull List<String> allowedPaths) {}
