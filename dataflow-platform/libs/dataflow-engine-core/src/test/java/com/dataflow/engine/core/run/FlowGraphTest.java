package com.dataflow.engine.core.run;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowGraphTest {

    private static Flow flow(String name, String... inputs) {
        return new Flow() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Set<String> inputs() {
                return Set.of(inputs);
            }

            @Override
            public FlowResult run(RunContext context) {
                return new FlowResult();
            }
        };
    }

    @Test
    void levels_groupFlowsWhoseInputsAreDone() {
        FlowGraph graph = new FlowGraph(List.of(
                flow("ingest"),
                flow("refs"),
                flow("bronze", "ingest"),
                flow("gold.a", "bronze", "refs"),
                flow("gold.b", "bronze"),
                flow("serving", "gold.a", "gold.b")));

        assertThat(graph.levels())
                .extracting(level -> level.stream().map(Flow::name).toList())
                .containsExactly(
                        List.of("ingest", "refs"), List.of("bronze"), List.of("gold.a", "gold.b"), List.of("serving"));
        assertThat(graph.downstreamOf("bronze")).containsExactlyInAnyOrder("gold.a", "gold.b", "serving");
    }

    @Test
    void rejectsAnUnknownInput() {
        assertThatThrownBy(() -> new FlowGraph(List.of(flow("bronze", "ingest"))))
                .hasMessageContaining("reads unknown flow ingest");
    }
}
