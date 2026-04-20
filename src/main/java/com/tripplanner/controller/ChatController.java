package com.tripplanner.controller;

import com.anthropic.models.messages.MessageParam;
import com.tripplanner.dto.request.ChatMessageRequest;
import com.tripplanner.dto.response.ChatResponse;
import com.tripplanner.dto.response.TripResponse;
import com.tripplanner.entity.User;
import com.tripplanner.repository.UserRepository;
import com.tripplanner.service.ClaudeService;
import com.tripplanner.service.TripService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ClaudeService claudeService;
    private final TripService tripService;
    private final UserRepository userRepository;

    // In-memory session store keyed by "userId:sessionId"
    private final Map<String, List<MessageParam>> sessions = new ConcurrentHashMap<>();

    @PostMapping("/message")
    public ChatResponse message(@RequestBody ChatMessageRequest request) {
        User user = currentUser();
        String sessionKey = user.getId() + ":" + (request.tripId() != null ? request.tripId() : "new");
        List<MessageParam> history = sessions.computeIfAbsent(sessionKey, k -> new ArrayList<>());

        String aiReply = claudeService.chat(history, request.message());

        history.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(request.message())
                .build());
        history.add(MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(aiReply)
                .build());

        boolean readyToGenerate = aiReply.toLowerCase().contains("ready to generate");
        return new ChatResponse(aiReply, readyToGenerate);
    }

    @PostMapping("/generate")
    public TripResponse generate(@RequestBody ChatMessageRequest request) {
        User user = currentUser();
        String sessionKey = user.getId() + ":" + (request.tripId() != null ? request.tripId() : "new");
        List<MessageParam> history = sessions.getOrDefault(sessionKey, List.of());

        String structurePrompt = """
                Based on our conversation, produce a JSON object with this exact structure (no markdown, raw JSON only):
                {
                  "destination": "string",
                  "departureCity": "string or null",
                  "startDate": "YYYY-MM-DD",
                  "endDate": "YYYY-MM-DD",
                  "budget": number,
                  "currency": "USD",
                  "style": "TIGHT|LOOSE|MIXED",
                  "stops": [
                    {
                      "locationName": "string",
                      "lat": number or null,
                      "lng": number or null,
                      "arrivalDate": "YYYY-MM-DD",
                      "departureDate": "YYYY-MM-DD",
                      "orderIndex": number,
                      "subPlaces": "comma-separated string",
                      "cost": {
                        "intercityTransport": number or null,
                        "localTransport": number or null,
                        "accommodation": number or null,
                        "food": number or null,
                        "activities": number or null,
                        "total": number
                      }
                    }
                  ],
                  "packingItems": [
                    { "category": "CLOTHING|TOILETRIES|ESSENTIALS|DESTINATION_SPECIFIC", "itemName": "string", "quantity": number }
                  ]
                }
                """;

        String json = claudeService.chat(history, structurePrompt);
        TripResponse response = tripService.toResponse(tripService.parseAndSaveTrip(user, json, request.tripId()));
        sessions.remove(sessionKey);
        return response;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
