package com.reconcileiq.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Replaces your existing Ollama implementation of AiInvestigationClient.
 * Uses Ollama's /api/chat with JSON mode and temperature 0.
 */
@Component
public class OllamaInvestigationClient implements AiInvestigationClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String model;

    public OllamaInvestigationClient(
            ObjectMapper mapper,
            @Value("${ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ai.ollama.model}") String model
    ) {
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public AiModelOutput investigate(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "think", false,
                    "stream", false,
                    "format", "json",
                    "options", Map.of("temperature", 0, "num_ctx", 4096),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "Ollama returned HTTP " + response.statusCode() + ": " + response.body());
            }

            String content = mapper.readTree(response.body())
                    .path("message").path("content").asText();

            return mapper.readValue(stripFences(content), AiModelOutput.class);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI investigation was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("AI investigation failed: " + e.getMessage(), e);
        }
    }

    // Defensive: JSON mode should prevent this, but small models sometimes wrap output anyway.
    private static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return t;
    }
}