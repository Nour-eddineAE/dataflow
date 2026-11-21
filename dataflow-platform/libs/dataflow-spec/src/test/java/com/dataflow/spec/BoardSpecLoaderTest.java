package com.dataflow.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardSpecLoaderTest {

    private final BoardSpecLoader loader = new BoardSpecLoader();

    @Test
    void parse_readsChartsToHideAndToPutFirst() {
        BoardSpec spec = loader.parse("""
            hide: [current/histogram-depthKm]
            first: [gold.daily_activity/time, current/bars-status]
            """, "dashboards/earthquakes.yml");

        assertThat(spec.hide()).containsExactly("current/histogram-depthKm");
        assertThat(spec.first()).containsExactly("gold.daily_activity/time", "current/bars-status");
        assertThat(BoardSpec.file("earthquakes")).isEqualTo("dashboards/earthquakes.yml");
    }

    @Test
    void parse_refusesUnknownKeysAndMalformedChartIds() {
        assertThatThrownBy(() -> loader.parse("pin: [current/time]\n", "dashboards/x.yml"))
                .isInstanceOf(InvalidProjectSpecException.class)
                .hasMessageContaining("unknown key 'pin'");
        assertThatThrownBy(() -> loader.parse("hide: [time]\n", "dashboards/x.yml"))
                .isInstanceOf(InvalidProjectSpecException.class)
                .hasMessageContaining("'time' is not <table>/<chart>");
        assertThat(loader.parse("", "dashboards/x.yml")).isEqualTo(BoardSpec.NONE);
    }
}
