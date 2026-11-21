package com.dataflow.engine.core.run;

public class FlowFailedException extends RuntimeException {

    private final transient FlowResult partial;

    public FlowFailedException(String message, FlowResult partial) {
        super(message);
        this.partial = partial;
    }

    public FlowResult partial() {
        return partial;
    }
}
