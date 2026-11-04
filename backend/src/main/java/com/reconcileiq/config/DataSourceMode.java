package com.reconcileiq.config;

public enum DataSourceMode {
    DEMO_SCENARIOS,
    LIVE_SANDBOX;

    public String recordSource() {
        return this == LIVE_SANDBOX ? "SANDBOX" : "DEMO";
    }

    public String displayLabel() {
        return this == LIVE_SANDBOX ? "Live PayPal Sandbox" : "Demo scenarios";
    }
}
