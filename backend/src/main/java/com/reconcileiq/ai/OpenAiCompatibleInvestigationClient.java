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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAiCompatibleInvestigationClient implements AiInvestigationClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final ReconcileIqProperties.Ai ai;

    public OpenAiCompatibleInvestigationClient(ObjectMapper mapper, ReconcileIqProperties properties) {
        this.mapper = mapper;
        this.ai = properties.getAi();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, ai.getTimeoutSeconds())))
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", ai.getModel());
            body.put("temperature", ai.getTemperature());
            body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));

            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(trimSlash(ai.getBaseUrl()) + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(ai.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (ai.getApiKey() != null && !ai.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + ai.getApiKey());
            }

            HttpResponse<String> response = http.send(builder.build(), BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "OpenAI-compatible provider returned HTTP "
                                + response.statusCode() + ": " + response.body());
            }
            return mapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content").asText();
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
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
