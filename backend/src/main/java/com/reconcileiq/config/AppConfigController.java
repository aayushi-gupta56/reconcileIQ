package com.reconcileiq.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AppConfigController {

    private final ReconcileIqProperties properties;
    private final String paypalClientId;

    public AppConfigController(
            ReconcileIqProperties properties,
            @Value("${paypal.client-id:}") String paypalClientId) {
        this.properties = properties;
        this.paypalClientId = paypalClientId;
    }

    @GetMapping({"/api/config", "/api/v1/config"})
    public Map<String, Object> config() {
        DataSourceMode mode = properties.getDataSource();
        boolean paypalConfigured = paypalClientId != null && !paypalClientId.isBlank();
        return Map.of(
                "dataSource", mode.name(),
                "source", mode.recordSource(),
                "label", mode.displayLabel(),
                "paypalConfigured", paypalConfigured
        );
    }
}
