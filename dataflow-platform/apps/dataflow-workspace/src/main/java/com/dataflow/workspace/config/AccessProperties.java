package com.dataflow.workspace.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties("dataflow.workspace.access")
@Validated
public record AccessProperties(@NotNull Duration refreshEvery) {}
