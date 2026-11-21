package com.dataflow.workspace.serving;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseMessageTest {

    @Test
    void message_dropsTheParserAlternativesAndTheScope() {
        String body =
                "Code: 62. DB::Exception: Syntax error: failed at position 1 (selec): selec x frm. Maybe you meant:"
                    + " SELECT. Expected one of: Query, SELECT query, WITH, FROM: In scope SELECT formatQuery('selec x"
                    + " frm'). (SYNTAX_ERROR) (version 26.10.2.103 (official build))";

        assertThat(ClickHouse.message(body))
                .isEqualTo("Syntax error: failed at position 1 (selec): selec x frm. Maybe you meant: SELECT."
                        + " (SYNTAX_ERROR)");
    }

    @Test
    void message_dropsAScopeThatFollowsASentence() {
        String body = "Code: 47. DB::Exception: Unknown expression identifier `regionx`. Maybe you meant: ['region']."
            + " In scope SELECT regionx FROM earthquakes.current. (UNKNOWN_IDENTIFIER) (version 26.10.2.103 (official"
            + " build))";

        assertThat(ClickHouse.message(body))
                .isEqualTo(
                        "Unknown expression identifier `regionx`. Maybe you meant: ['region']. (UNKNOWN_IDENTIFIER)");
    }

    @Test
    void message_keepsAPlainError() {
        String body = "Code: 60. DB::Exception: Unknown table expression identifier 'nope'. (UNKNOWN_TABLE) (version"
                + " 26.10.2.103 (official build))";

        assertThat(ClickHouse.message(body)).isEqualTo("Unknown table expression identifier 'nope'. (UNKNOWN_TABLE)");
    }
}
