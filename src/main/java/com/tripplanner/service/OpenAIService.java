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
import com.tripplanner.dto.response.PackingItemResponse;
import com.tripplanner.entity.*;
import com.tripplanner.enums.DayIntensity;
import com.tripplanner.enums.PackingCategory;
import com.tripplanner.enums.PlaceType;
import com.tripplanner.enums.TripCategory;
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

    private static final String META_DELIMITER = "---META---";
    private static final String STOP_DELIMITER = "---STOP---";
    private static final String DONE_DELIMITER = "---DONE---";

    private static final String TRIP_SYSTEM_PROMPT = """
            You are an expert travel planner. Generate a detailed trip itinerary as a sequence of location stops.

            First, output a meta JSON object immediately followed by: ---META---
            Then output each stop as a valid JSON object immediately followed by: ---STOP---
            After all stops, output a packing list JSON followed by: ---DONE---
            Output ONLY JSON and delimiters — no explanation, no markdown, no extra text.

            Meta JSON format (single line):
            {"title":"Cherry Blossoms & Ancient Temples","shortDescription":"10 days through Tokyo and Kyoto chasing spring blooms, street food, and centuries-old shrines."}---META---

            Stop JSON format (single line):
            {"locationName":"City, Country","lat":35.6762,"lng":139.6503,"arrivalDate":"YYYY-MM-DD","departureDate":"YYYY-MM-DD","orderIndex":0,"hotel":{"name":"Hotel Name","starRating":4,"pricePerNight":150.00,"address":"Hotel address","lat":35.6762,"lng":139.6503},"restaurants":[{"dayDate":"YYYY-MM-DD","name":"Restaurant Name","cuisine":"Cuisine Type","priceLevel":2,"rating":4.5,"address":"Restaurant address","lat":35.6762,"lng":139.6503}],"itinerary":[{"dayDate":"YYYY-MM-DD","orderIndex":0,"placeName":"Hotel Name","placeType":"ACCOMMODATION","startTime":"14:00","durationMins":60,"distanceFromPrevMiles":0.0,"commuteFromPrevMins":0,"commuteMode":"NONE","lat":35.6762,"lng":139.6503},{"dayDate":"YYYY-MM-DD","orderIndex":1,"placeName":"Shinjuku Gyoen","placeType":"ATTRACTION","startTime":"15:30","durationMins":90,"distanceFromPrevMiles":1.2,"commuteFromPrevMins":12,"commuteMode":"WALK","lat":35.6851,"lng":139.7100},{"dayDate":"YYYY-MM-DD","orderIndex":2,"placeName":"Ichiran Ramen","placeType":"RESTAURANT","startTime":"19:00","durationMins":60,"distanceFromPrevMiles":0.8,"commuteFromPrevMins":10,"commuteMode":"WALK","lat":35.6921,"lng":139.7006}],"cost":{"intercityTransport":200.00,"intercityTransportType":"flight","localTransport":30.00,"accommodation":120.00,"food":40.00,"activities":30.00,"total":420.00,"intercityPublicTransportTimeMins":90,"intercityCarTimeMins":45}}---STOP---

            Packing list JSON format:
            {"packingItems":[{"category":"CLOTHING","itemName":"T-shirts","quantity":7}]}---DONE---

            Rules:
            - title: short evocative trip name (5-8 words), not just "Trip to X"
            - shortDescription: 1-2 sentences capturing the trip's essence and highlights
            - First stop: set intercityPublicTransportTimeMins and intercityCarTimeMins to 0
            - intercityTransportType: "flight", "train", "bus", or "car"
            - All dates must be within the trip date range
            - LIGHT intensity = fewer stops with longer stays; BALANCED = moderate; PACKED = many stops
            - PackingCategory values: CLOTHING, TOILETRIES, ESSENTIALS, DESTINATION_SPECIFIC
            - Include exactly one hotel per stop; if the user mentioned a specific hotel near that location, use it
            - Include one restaurant per day at each stop (restaurants array length = number of days at that stop)
            - Honour any food preferences and specific restaurants mentioned by the user
            - priceLevel: 1=budget, 2=mid-range, 3=expensive
            - itinerary: one entry per activity per day; first item each day is always ACCOMMODATION (hotel check-in or wake-up) with distanceFromPrevMiles=0 and commuteFromPrevMins=0
            - placeType values: ACCOMMODATION, RESTAURANT, ATTRACTION, TRANSPORT
            - commuteMode values: WALK, TAXI, SUBWAY, CAR, BUS, NONE
            - distanceFromPrevMiles and commuteFromPrevMins reflect travel from the previous activity that day
            - startTime format: HH:mm (24-hour)
            - itinerary restaurant entries must match a name in the restaurants array for that day
            """;

    @PostConstruct
    public void init() {
        client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }

    public void generateTripStream(User user, TripCreateRequest req, SseEmitter emitter) {
        String userPrompt = buildTripPrompt(req);
        streamAndSave(userPrompt, emitter, (stopJsons, packingJson, title, shortDescription) -> {
            Trip trip = tripService.parseAndSaveFromStops(user, req, stopJsons, packingJson, title, shortDescription);
            return trip.getId();
        });
    }

    public void regenerateTripStream(User user, Long tripId, SseEmitter emitter) {
        Trip trip = tripService.clearAndPrepareForRegenerate(tripId, user.getId());
        String userPrompt = buildTripPromptFromTrip(trip);
        streamAndSave(userPrompt, emitter, (stopJsons, packingJson, title, shortDescription) -> {
            tripService.applyRegeneratedContent(tripId, user.getId(), stopJsons, packingJson, title, shortDescription);
            return tripId;
        });
    }

    public void refineTripStream(User user, Long tripId, String refinementRequest, SseEmitter emitter) {
        Trip existingTrip = tripService.getTripForRefine(tripId, user.getId());
        String userPrompt = buildRefinePrompt(existingTrip, refinementRequest);
        streamAndSave(userPrompt, emitter, (stopJsons, packingJson, title, shortDescription) -> {
            tripService.replaceTrip(tripId, user.getId(), stopJsons, packingJson, title, shortDescription);
            return tripId;
        });
    }

    public List<PackingItemResponse> refinePackingList(Trip trip, List<PackingItem> currentItems, String refinementRequest) {
        String categories = trip.getCategories().stream()
                .map(c -> c.name().toLowerCase().replace("_", " "))
                .collect(Collectors.joining(", "));

        StringBuilder currentList = new StringBuilder();
        currentItems.forEach(item ->
                currentList.append("- ").append(item.getCategory()).append(": ")
                        .append(item.getItemName()).append(" (qty: ").append(item.getQuantity()).append(")\n"));

        String prompt = String.format(
                "Current packing list for a trip to %s (%s to %s, styles: %s, intensity: %s):\n%s\n" +
                "User wants these changes: %s\n" +
                "Return an updated complete packing list as JSON: {\"packingItems\":[{\"category\":\"CLOTHING\",\"itemName\":\"T-shirts\",\"quantity\":7}]}\n" +
                "PackingCategory values: CLOTHING, TOILETRIES, ESSENTIALS, DESTINATION_SPECIFIC",
                trip.getDestination(), trip.getStartDate(), trip.getEndDate(),
                categories, trip.getIntensity().name().toLowerCase(),
                currentList, refinementRequest);

        ChatCompletion completion = client.chat().completions().create(
                ChatCompletionCreateParams.builder()
                        .model(ChatModel.GPT_4O)
                        .addSystemMessage("You are a travel assistant. Return only a valid JSON object, no markdown.")
                        .addUserMessage(prompt)
                        .maxTokens(1500L)
                        .build()
        );

        String json = completion.choices().get(0).message().content().orElse("{\"packingItems\":[]}");
        return parsePackingListJson(json);
    }

    private void streamAndSave(String userPrompt, SseEmitter emitter, TripSaveCallback callback) {
        StringBuilder buffer = new StringBuilder();
        List<String> stopJsons = new ArrayList<>();
        String[] meta = {null, null}; // [title, shortDescription]

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

                int metaIdx;
                if (meta[0] == null && (metaIdx = buffer.indexOf(META_DELIMITER)) >= 0) {
                    String metaJson = buffer.substring(0, metaIdx).trim();
                    buffer.delete(0, metaIdx + META_DELIMITER.length());
                    try {
                        JsonNode node = objectMapper.readTree(metaJson);
                        meta[0] = node.path("title").asText(null);
                        meta[1] = node.path("shortDescription").asText(null);
                        emitter.send(SseEmitter.event().name("meta").data(metaJson));
                    } catch (Exception e) {
                        log.warn("Failed to parse meta JSON: {}", metaJson);
                    }
                }

                int delimIdx;
                while ((delimIdx = buffer.indexOf(STOP_DELIMITER)) >= 0) {
                    String stopJson = buffer.substring(0, delimIdx).trim();
                    buffer.delete(0, delimIdx + STOP_DELIMITER.length());
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
            int doneIdx = remaining.indexOf(DONE_DELIMITER);
            String packingJson;
            if (doneIdx >= 0) {
                packingJson = remaining.substring(0, doneIdx).trim();
            } else {
                log.warn("Stream ended without {} delimiter. Attempting best-effort packing list parse.", DONE_DELIMITER);
                packingJson = remaining.trim();
            }

            Long tripId = callback.save(stopJsons, packingJson, meta[0], meta[1]);
            emitter.send(SseEmitter.event().name("complete").data(tripId));
            emitter.complete();

        } catch (UncheckedIOException e) {
            sendErrorAndComplete(emitter, "Connection lost during generation. Please try again.");
        } catch (Exception e) {
            log.error("Trip generation failed", e);
            sendErrorAndComplete(emitter, "Generation failed. Please try again.");
        }
    }

    private void sendErrorAndComplete(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.complete();
        } catch (Exception ignored) {
            try { emitter.completeWithError(new RuntimeException(message)); } catch (Exception alsoIgnored) {}
        }
    }

    private String buildTripPrompt(TripCreateRequest req) {
        LocalDate endDate = req.startDate().plusDays(req.totalDays() - 1);
        return buildTripPromptBase(req.destination(), req.startDate(), endDate, req.totalDays(),
                req.categories(), req.intensity(), req.budget(),
                req.currency() != null ? req.currency() : "USD", req.additionalInfo());
    }

    private String buildTripPromptFromTrip(Trip trip) {
        long totalDays = trip.getStartDate().until(trip.getEndDate()).getDays() + 1;
        return buildTripPromptBase(trip.getDestination(), trip.getStartDate(), trip.getEndDate(),
                totalDays, trip.getCategories(), trip.getIntensity(),
                trip.getBudget(), trip.getCurrency(), trip.getAdditionalInfo());
    }

    private String buildTripPromptBase(String destination, LocalDate startDate, LocalDate endDate,
                                       long totalDays, List<TripCategory> categories,
                                       DayIntensity intensity, BigDecimal budget, String currency,
                                       String additionalInfo) {
        String cats = categories.stream()
                .map(c -> c.name().toLowerCase().replace("_", " "))
                .collect(Collectors.joining(", "));
        String base = String.format(
                "Plan a trip to %s from %s to %s (%d days). Trip styles: %s. Day intensity: %s. Total budget: %s %s.",
                destination, startDate, endDate, totalDays, cats,
                intensity.name().toLowerCase(), budget, currency);
        if (additionalInfo != null && !additionalInfo.isBlank()) {
            base += " Additional requirements: " + additionalInfo;
        }
        return base;
    }

    private String buildRefinePrompt(Trip trip, String refinementRequest) {
        String categories = trip.getCategories().stream()
                .map(c -> c.name().toLowerCase().replace("_", " "))
                .collect(Collectors.joining(", "));

        StringBuilder context = new StringBuilder();
        context.append("Current trip to ").append(trip.getDestination())
               .append(" (").append(trip.getStartDate()).append(" to ").append(trip.getEndDate()).append("):\n");
        if (trip.getStops() != null) {
            for (TripStop stop : trip.getStops()) {
                context.append("Stop ").append(stop.getOrderIndex() + 1).append(": ")
                       .append(stop.getLocationName())
                       .append(" (").append(stop.getArrivalDate()).append(" to ").append(stop.getDepartureDate()).append(")")
                       .append(", places: ").append(stop.getItineraryItems().stream()
                               .filter(i -> PlaceType.ATTRACTION == i.getPlaceType())
                               .map(ItineraryItem::getPlaceName)
                               .collect(Collectors.joining(", ")));
                if (!stop.getHotels().isEmpty()) {
                    context.append(", hotel: ").append(stop.getHotels().get(0).getName());
                }
                if (!stop.getRestaurants().isEmpty()) {
                    context.append(", restaurants: ");
                    stop.getRestaurants().forEach(r ->
                            context.append(r.getName()).append(" (").append(r.getDayDate()).append(") "));
                }
                context.append("\n");
            }
        }

        return String.format(
                "Here is the user's current trip:\n%s\n" +
                "Original parameters: destination=%s, dates=%s to %s, styles=%s, intensity=%s, budget=%s %s.\n" +
                "User wants these changes: %s\n" +
                "Generate a new complete trip following the exact same JSON/delimiter format.",
                context, trip.getDestination(), trip.getStartDate(), trip.getEndDate(),
                categories, trip.getIntensity().name().toLowerCase(),
                trip.getBudget(), trip.getCurrency(), refinementRequest);
    }

    private List<PackingItemResponse> parsePackingListJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<PackingItemResponse> results = new ArrayList<>();
            for (JsonNode p : root.path("packingItems")) {
                results.add(new PackingItemResponse(
                        null,
                        PackingCategory.valueOf(p.path("category").asText("ESSENTIALS")),
                        p.path("itemName").asText(),
                        p.path("quantity").asInt(1),
                        false));
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to parse packing list JSON: {}", json, e);
            return List.of();
        }
    }

    @FunctionalInterface
    private interface TripSaveCallback {
        Long save(List<String> stopJsons, String packingJson, String title, String shortDescription) throws Exception;
    }
}
