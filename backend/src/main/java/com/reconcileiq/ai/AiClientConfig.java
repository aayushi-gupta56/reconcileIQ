package com.reconcileiq.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcileiq.config.ReconcileIqProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfig {

    @Bean
    public AiInvestigationClient aiInvestigationClient(
            ObjectMapper mapper,
            ReconcileIqProperties properties
    ) {
        String provider = properties.getAi().getProvider();
        if (provider != null && provider.equalsIgnoreCase("OPENAI_COMPATIBLE")) {
            return new OpenAiCompatibleInvestigationClient(mapper, properties);
        }
        return new OllamaInvestigationClient(mapper, properties);
    }
}
