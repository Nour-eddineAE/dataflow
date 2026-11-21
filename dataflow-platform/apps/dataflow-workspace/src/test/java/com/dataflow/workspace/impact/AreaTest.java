package com.dataflow.workspace.impact;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AreaTest {

    @Test
    void writesTheLowerCaseIdTheScreenShows() {
        assertThat(JsonMapper.builder().build().writeValueAsString(Area.SILVER)).isEqualTo("\"silver\"");
    }
}
