package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.setup.EffectiveSettings;
import com.dataflow.workspace.setup.PlatformServices;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PlatformController {

    private final PlatformServices services;
    private final EffectiveSettings settings;
    private final Access access;

    public PlatformController(PlatformServices services, EffectiveSettings settings, Access access) {
        this.services = services;
        this.settings = settings;
        this.access = access;
    }

    @GetMapping("/services")
    public List<PlatformServices.PlatformService> services() {
        return services.visible();
    }

    @GetMapping("/settings/effective")
    public EffectiveSettings.View effective() {
        access.requireAdmin();
        return settings.view();
    }
}
