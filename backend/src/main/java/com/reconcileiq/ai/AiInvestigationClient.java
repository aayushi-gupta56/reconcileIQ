package com.reconcileiq.ai;

public interface AiInvestigationClient {

    AiModelOutput investigate(String systemPrompt, String userPrompt);
}