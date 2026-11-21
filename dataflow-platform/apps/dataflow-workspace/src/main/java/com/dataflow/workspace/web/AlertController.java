package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.alerts.Alerts;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final Alerts alerts;
    private final Access access;

    public AlertController(Alerts alerts, Access access) {
        this.alerts = alerts;
        this.access = access;
    }

    @GetMapping
    public Alerts.Current current() {
        Alerts.Current current = alerts.current();
        Caller caller = access.caller();
        return new Alerts.Current(
                current.available(),
                current.reason(),
                current.alerts().stream()
                        .filter(alert -> alert.dataset() == null ? caller.admin() : access.sees(caller, alert.dataset()))
                        .toList());
    }
}
