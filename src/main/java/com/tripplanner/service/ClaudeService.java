package com.tripplanner.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClaudeService {

    @Value("${anthropic.api-key}")
    private String apiKey;

    private AnthropicClient client;

    private static final String SYSTEM_PROMPT = """
            You are TripPlanner AI, an expert travel planner assistant.
            Help users plan trips by collecting: destination, travel dates, total budget, travel style (Tight/Loose/Mixed), departure city (optional), and any known places they want to visit.

            Travel style definitions:
            - Tight: maximize locations, 1-2 days per stop
            - Loose: fewer locations, 4-7 days per stop, immersive
            - Mixed: you decide the pace per location based on what it offers

            Once you have enough info, produce a detailed itinerary summary with:
            - Each location stop: name, date range, sub-places to visit
            - Cost breakdown per stop: intercity transport (flight/train/bus), local transport (transit/taxi/car rental), accommodation, food, activities
            - Running total vs budget
            - Packing list

            Use Claude's knowledge of typical prices for the region and season — mark all prices as estimates.
            End your summary with: "Ready to generate your plan? Reply 'yes' to create the map and list view."
            When user confirms, set readyToGenerate=true in your response.
            For editing requests, update the relevant parts of the itinerary and explain changes clearly.
            """;

    @PostConstruct
    public void init() {
        client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }

    public String chat(List<com.anthropic.models.messages.MessageParam> history, String userMessage) {
        List<com.anthropic.models.messages.MessageParam> messages = new java.util.ArrayList<>(history);
        messages.add(com.anthropic.models.messages.MessageParam.builder()
                .role(com.anthropic.models.messages.MessageParam.Role.USER)
                .content(userMessage)
                .build());

        Message response = client.messages().create(
                MessageCreateParams.builder()
                        .model(Model.of("claude-sonnet-4-6"))
                        .maxTokens(4096)
                        .system(SYSTEM_PROMPT)
                        .messages(messages)
                        .build());

        return response.content().stream()
                .filter(block -> block.isText())
                .map(block -> block.asText().text())
                .findFirst()
                .orElse("");
    }
}
