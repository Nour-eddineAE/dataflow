package com.dataflow.engine.core.serving;

public record ServingSettings(String database, String collection, String refreshEvery) {}
