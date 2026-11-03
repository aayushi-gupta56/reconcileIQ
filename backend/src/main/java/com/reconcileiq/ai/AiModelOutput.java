package com.reconcileiq.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Raw JSON the model returns. Internal to the AI layer; the service maps it
 * into InvestigationResponse so the entity and UI stay unchanged.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiModelOutput(
        String summary,
        List<String> observedFacts,
        List<String> possibleExplanations,
        List<String> recommendedChecks,
        boolean requiresHumanReview
) {}