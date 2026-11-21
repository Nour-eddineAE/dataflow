package com.dataflow.workspace.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("dataflow.workspace.http")
@Validated
public record HttpProperties(@NotNull Duration connectTimeout, @NotNull Duration probeTimeout) {}
