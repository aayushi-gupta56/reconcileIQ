package com.reconcileiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reconcileiq")
public class ReconcileIqProperties {

    /**
     * DEMO_SCENARIOS runs seeded local data with no PayPal credentials.
     * LIVE_SANDBOX uses real PayPal sandbox ingestion.
     */
    private DataSourceMode dataSource = DataSourceMode.DEMO_SCENARIOS;

    private final Ai ai = new Ai();

    public DataSourceMode getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSourceMode dataSource) {
        this.dataSource = dataSource == null ? DataSourceMode.DEMO_SCENARIOS : dataSource;
    }

    public Ai getAi() {
        return ai;
    }

    public static class Ai {
        public static final String PROMPT_VERSION = "v1";

        private String provider = "OLLAMA";
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen3:8b";
        private double temperature = 0.1;
        private int timeoutSeconds = 60;
        private String promptVersion = PROMPT_VERSION;
        private String apiKey = "";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getPromptVersion() {
            return promptVersion;
        }

        public void setPromptVersion(String promptVersion) {
            this.promptVersion = promptVersion;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
