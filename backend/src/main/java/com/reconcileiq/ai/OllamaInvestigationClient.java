package com.reconcileiq.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcileiq.config.ReconcileIqProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class OllamaInvestigationClient implements AiInvestigationClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final ReconcileIqProperties.Ai ai;

    public OllamaInvestigationClient(ObjectMapper mapper, ReconcileIqProperties properties) {
        this.mapper = mapper;
        this.ai = properties.getAi();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMinutes(5))
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", ai.getModel(),
                    "think", false,
                    "stream", false,
                    "format", "json",
                    "options", Map.of("temperature", ai.getTemperature()),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(ai.getBaseUrl()) + "/api/chat"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "Ollama returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return mapper.readTree(response.body()).path("message").path("content").asText();
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException("AI request timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI investigation was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("AI investigation failed: " + e.getMessage(), e);
        }
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.endsWith("/")) {
            return baseUrl == null ? "" : baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
