package com.dataflow.engine.core.run;

import java.time.Duration;

public record HttpTimeouts(Duration connect, Duration response) {}
