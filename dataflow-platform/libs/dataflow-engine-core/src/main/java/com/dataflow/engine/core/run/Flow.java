package com.dataflow.engine.core.run;

import java.util.Set;

public interface Flow {

    String name();

    Set<String> inputs();

    FlowResult run(RunContext context);
}
