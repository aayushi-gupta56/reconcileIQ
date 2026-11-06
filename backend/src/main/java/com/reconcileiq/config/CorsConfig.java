package com.reconcileiq.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Single, global CORS policy for every /api/** endpoint.
 *
 * Allowed origins come from reconcileiq.cors.allowed-origins
 * (env var RECONCILEIQ_CORS_ALLOWED_ORIGINS, comma-separated). The default is
 * exactly the deployed frontend plus the local Vite dev server.
 *
 * Spring answers preflight (OPTIONS) requests itself for any mapped path, so no
 * controller or security changes are needed. This project has no Spring Security.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(
            @Value("${reconcileiq.cors.allowed-origins:https://reconcileiq-frontend.onrender.com,http://localhost:5173}")
            String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(origin -> origin.replaceAll("/+$", ""))
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*") // Content-Type, Authorization, Accept, ...
                .allowCredentials(false) // no cookies/sessions are used
                .maxAge(3600);
    }
}
