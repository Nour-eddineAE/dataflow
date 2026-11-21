package com.dataflow.workspace.specs;

import com.dataflow.workspace.Drafts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemLocatorTest {

    private final String earthquakes = Drafts.text("earthquakes");

    @Test
    void locate_readsTheLineOfAYamlError() {
        assertThat(ProblemLocator.locate(earthquakes, "line 7: Unrecognized field \"sorce\""))
                .isEqualTo(new Problem("Unrecognized field \"sorce\"", 7));
    }

    @Test
    void locate_turnsAYamlSyntaxErrorIntoOneLineAtTheKey() {
        String message = "line 3: while scanning a simple key\n in 'reader', line 4, column 1:\n    sched\n    ^\n"
                + "could not find expected ':'\n in 'reader', line 5, column 1:\n    description: USGS ... \n    ^\n";

        assertThat(ProblemLocator.locate(earthquakes, message))
                .isEqualTo(new Problem("could not find expected ':'", 4));
    }

    @Test
    void locate_pointsAtTheNamedColumn() {
        Problem problem = ProblemLocator.locate(earthquakes, "silver.columns.magnitude.type: cannot parse 'dubble'");

        assertThat(earthquakes.lines().toList().get(problem.line() - 1)).contains("name: magnitude,");
    }

    @Test
    void locate_fallsBackToTheSection() {
        Problem problem = ProblemLocator.locate(earthquakes, "schedule must be a five-field cron expression, got 'x'");

        assertThat(earthquakes.lines().toList().get(problem.line() - 1)).startsWith("schedule:");
    }
}
