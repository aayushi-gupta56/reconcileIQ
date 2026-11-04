package com.reconcileiq.paypal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PayPalConfig {

    @Bean
    public RestClient paypalRestClient(
            @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl) {

        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}