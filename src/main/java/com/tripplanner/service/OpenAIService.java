package com.tripplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.tripplanner.dto.request.TripCreateRequest;
import com.tripplanner.dto.response.HotelResponse;
import com.tripplanner.dto.response.RestaurantResponse;
import com.tripplanner.entity.Trip;
import com.tripplanner.entity.TripStop;
import com.tripplanner.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

    @Value("${openai.api-key}")
    private String apiKey;

    private OpenAIClient client;

    private final TripService tripService;
    private final ObjectMapper objectMapper;

    private static final String TRIP_SYSTEM_PROMPT = """
            You are an expert travel planner. Generate a detailed trip itinerary as a sequence of location stops.

            Output each stop as a valid JSON object immediately followed by the exact delimiter: ---STOP---
            After all stops, output a packing list JSON followed by: ---DONE---
            Output ONLY JSON and delimiters — no explanation, no markdown, no extra text.

            Stop JSON format (single line):
            {"locationName":"City, Country","lat":35.6762,"lng":139.6503,"arrivalDate":"YYYY-MM-DD","departureDate":"YYYY-MM-DD","orderIndex":0,"subPlaces":"Place1, Place2, Place3","cost":{"intercityTransport":200.00,"intercityTransportType":"flight","localTransport":30.00,"accommodation":120.00,"food":40.00,"activities":30.00,"total":420.00,"intercityPublicTransportTimeMins":90,"intercityCarTimeMins":45}}---STOP---

            Packing list JSON format:
            {"packingItems":[{"category":"CLOTHING","itemName":"T-shirts","quantity":7}]}---DONE---

            Rules:
            - First stop: set intercityPublicTransportTimeMins and intercityCarTimeMins to 0
            - intercityTransportType: "flight", "train", "bus", or "car"
            - All dates must be within the trip date range
            - LIGHT intensity = fewer stops with longer stays; BALANCED = moderate; PACKED = many stops
            - PackingCategory values: CLOTHING, TOILETRIES, ESSENTIALS, DESTINATION_SPECIFIC
            """;

    @PostConstruct
    public void init() {
        client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }

    public void generateTripStream(User user, TripCreateRequest req, SseEmitter emitter) {
        String userPrompt = buildTripPrompt(req);
        StringBuilder buffer = new StringBuilder();
        List<String> stopJsons = new ArrayList<>();

        try (StreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(
                ChatCompletionCreateParams.builder()
                        .model(ChatModel.GPT_4O)
                        .addSystemMessage(TRIP_SYSTEM_PROMPT)
                        .addUserMessage(userPrompt)
                        .maxTokens(8000L)
                        .build()
        )) {
            stream.stream().forEach(chunk -> {
                if (chunk.choices().isEmpty()) return;
                String delta = chunk.choices().get(0).delta().content().orElse("");
                buffer.append(delta);

                int delimIdx;
                while ((delimIdx = buffer.indexOf("---STOP---")) >= 0) {
                    String stopJson = buffer.substring(0, delimIdx).trim();
                    buffer.delete(0, delimIdx + 10);
                    if (!stopJson.isBlank()) {
                        stopJsons.add(stopJson);
                        try {
                            emitter.send(SseEmitter.event().name("stop").data(stopJson));
                        } catch (IOException e) {
                            log.info("Client disconnected during trip stream, aborting generation");
                            throw new UncheckedIOException(e);
                        }
                    }
                }
            });

            String remaining = buffer.toString();
            int doneIdx = remaining.indexOf("---DONE---");
            String packingJson;
            if (doneIdx >= 0) {
                packingJson = remaining.substring(0, doneIdx).trim();
            } else {
                log.warn("Stream ended without ---DONE--- delimiter. Attempting best-effort packing list parse.");
                packingJson = remaining.trim();
            }

            Trip trip = tripService.parseAndSaveFromStops(user, req, stopJsons, packingJson);
            emitter.send(SseEmitter.event().name("complete").data(trip.getId()));
            emitter.complete();

        } catch (UncheckedIOException e) {
            try { emitter.completeWithError(e.getCause()); } catch (Exception ignored) {}
        } catch (Exception e) {
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }

    public List<RestaurantResponse> getRestaurantRecommendations(TripStop stop, LocalDate date, String preferences) {
        String extra = (preferences != null && !preferences.isBlank()) ? " Preferences: " + preferences + "." : "";
        String prompt = String.format(
                "Recommend 5 restaurants near %s for a visit on %s.%s " +
                "Return a JSON array: [{\"name\":\"...\",\"cuisine\":\"...\",\"priceLevel\":2,\"rating\":4.5,\"address\":\"...\",\"lat\":0.0,\"lng\":0.0}]",
                stop.getLocationName(), date, extra);

        ChatCompletion completion = client.chat().completions().create(
                ChatCompletionCreateParams.builder()
                        .model(ChatModel.GPT_4O)
                        .addSystemMessage("You are a travel assistant. Return only a valid JSON array, no markdown.")
                        .addUserMessage(prompt)
                        .maxTokens(1500L)
                        .build()
        );

        String json = completion.choices().get(0).message().content().orElse("[]");
        return parseRestaurantArray(json, stop.getId(), date);
    }

    public List<HotelResponse> getHotelRecommendations(TripStop stop, String preferences) {
        String extra = (preferences != null && !preferences.isBlank()) ? " Preferences: " + preferences + "." : "";
        String prompt = String.format(
                "Recommend 5 hotels in %s for a stay from %s to %s.%s " +
                "Return a JSON array: [{\"name\":\"...\",\"starRating\":4,\"pricePerNight\":120.0,\"address\":\"...\",\"lat\":0.0,\"lng\":0.0}]",
                stop.getLocationName(), stop.getArrivalDate(), stop.getDepartureDate(), extra);

        ChatCompletion completion = client.chat().completions().create(
                ChatCompletionCreateParams.builder()
                        .model(ChatModel.GPT_4O)
                        .addSystemMessage("You are a travel assistant. Return only a valid JSON array, no markdown.")
                        .addUserMessage(prompt)
                        .maxTokens(1500L)
                        .build()
        );

        String json = completion.choices().get(0).message().content().orElse("[]");
        return parseHotelArray(json, stop.getId());
    }

    private String buildTripPrompt(TripCreateRequest req) {
        LocalDate endDate = req.startDate().plusDays(req.totalDays() - 1);
        String categories = req.categories().stream()
                .map(c -> c.name().toLowerCase().replace("_", " "))
                .collect(Collectors.joining(", "));
        String base = String.format(
                "Plan a trip to %s from %s to %s (%d days). " +
                "Trip styles: %s. Day intensity: %s. Total budget: %s %s.",
                req.destination(), req.startDate(), endDate, req.totalDays(),
                categories, req.intensity().name().toLowerCase(),
                req.budget(), req.currency() != null ? req.currency() : "USD");
        if (req.additionalInfo() != null && !req.additionalInfo().isBlank()) {
            base += " Additional requirements: " + req.additionalInfo();
        }
        return base;
    }

    private List<RestaurantResponse> parseRestaurantArray(String json, Long stopId, LocalDate date) {
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<RestaurantResponse> results = new ArrayList<>();
            for (JsonNode node : arr) {
                results.add(new RestaurantResponse(
                        null, stopId, date,
                        node.path("name").asText(),
                        node.path("cuisine").asText(),
                        node.path("priceLevel").asInt(2),
                        node.path("rating").asDouble(4.0),
                        node.path("address").asText(),
                        node.path("lat").isNull() ? null : node.path("lat").asDouble(),
                        node.path("lng").isNull() ? null : node.path("lng").asDouble(),
                        false));
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to parse restaurant recommendations JSON for stop {}: {}", stopId, json, e);
            return List.of();
        }
    }

    private List<HotelResponse> parseHotelArray(String json, Long stopId) {
        try {
            JsonNode arr = objectMapper.readTree(json);
            List<HotelResponse> results = new ArrayList<>();
            for (JsonNode node : arr) {
                BigDecimal price = node.path("pricePerNight").isNull() ? null
                        : BigDecimal.valueOf(node.path("pricePerNight").asDouble());
                results.add(new HotelResponse(
                        null, stopId,
                        node.path("name").asText(),
                        node.path("starRating").asInt(3),
                        price,
                        node.path("address").asText(),
                        node.path("lat").isNull() ? null : node.path("lat").asDouble(),
                        node.path("lng").isNull() ? null : node.path("lng").asDouble(),
                        false));
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to parse hotel recommendations JSON for stop {}: {}", stopId, json, e);
            return List.of();
        }
    }
}
