package com.reconcileiq.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReconcileIqProperties.class)
public class ReconcileIqConfig {
}
