package com.dataflow.workspace.alerts;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AlertsTest {

    @Test
    void firingAndPendingAlertsAreKeptWithTheirDataset() throws Exception {
        List<Alert> alerts = Alerts.parse(new ObjectMapper().readTree("""
            {"status":"success","data":{"alerts":[
              {"labels":{"alertname":"Dataset late against its schedule","dataset":"earthquakes","severity":"critical"},
               "annotations":{"summary":"earthquakes is 2.5 hours late against its schedule"},
               "state":"Alerting","activeAt":"2026-10-26T09:00:00Z","value":"2.5"},
              {"labels":{"alertname":"Quarantine rate above 5%","dataset":"taxi_trips"},"state":"Normal"},
              {"labels":{"alertname":"Published copy not refreshing"},"state":"Normal (NoData)",
               "annotations":{"summary":"{{ $labels.copy }} failed its last refresh"}},
              {"labels":{"alertname":"Dataset late against its schedule","dataset":"taxi_trips"},"state":"Alerting (NoData)"},
              {"labels":{"alertname":"Quarantine rate above 5%","dataset":"taxi_trips"},"state":"Pending","activeAt":"2026-10-26T09:05:00Z"}
            ]}}
            """));

        assertThat(alerts)
                .extracting(Alert::dataset, Alert::state)
                .containsExactly(
                        tuple("earthquakes", "alerting"),
                        tuple("taxi_trips", "alerting (nodata)"),
                        tuple("taxi_trips", "pending"));
        assertThat(alerts.getFirst().summary()).isEqualTo("earthquakes is 2.5 hours late against its schedule");
    }
}
