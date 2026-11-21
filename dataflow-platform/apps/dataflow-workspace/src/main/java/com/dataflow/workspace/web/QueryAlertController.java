package com.dataflow.workspace.web;

import com.dataflow.workspace.queries.QueryAlert;
import com.dataflow.workspace.queries.QueryAlertView;
import com.dataflow.workspace.queries.QueryAlerts;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sql")
public class QueryAlertController {

    private final QueryAlerts alerts;

    public QueryAlertController(QueryAlerts alerts) {
        this.alerts = alerts;
    }

    @GetMapping("/alerts")
    public List<QueryAlertView> alerts() {
        return alerts.visible();
    }

    @PutMapping("/saved/{id}/alert")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@PathVariable UUID id, @RequestBody AlertSettings body) {
        alerts.save(id, body.everyMinutes(), body.comparison(), body.threshold());
    }

    @DeleteMapping("/saved/{id}/alert")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        alerts.delete(id);
    }

    public record AlertSettings(int everyMinutes, QueryAlert.Comparison comparison, BigDecimal threshold) {}
}
