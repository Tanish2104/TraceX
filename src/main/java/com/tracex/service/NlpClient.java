package com.tracex.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Talks to the Python AI service (tracex-nlp, spaCy). Every method fails soft: when the service is off or
 * returns something unexpected the caller gets an empty result and TraceX carries on with pattern matching.
 */
@Component
public class NlpClient {

    /** What the service returned for a list of texts, in the same order as the request. */
    public record NlpResult(String model, List<EntityExtractor.Extracted> results) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(NlpClient.class);
    private static final int CHUNK = 500;

    private final boolean enabled;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public NlpClient(@Value("${tracex.nlp.enabled:true}") boolean enabled,
                     @Value("${tracex.nlp.url:http://127.0.0.1:8001}") String baseUrl) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // HTTP/1.1 only: the Python server does not support the HTTP/2 upgrade that Java tries by default.
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** One-line description shown on the upload page. */
    public String status() {
        if (!enabled) {
            return "OFF: switched off in application.properties. Pattern matching is used.";
        }
        Optional<String> model = health();
        if (model.isPresent()) {
            return "ON: spaCy model " + model.get() + " is running at " + baseUrl + ".";
        }
        return "OFF: the AI service is not running at " + baseUrl
                + ". Uploads still work using pattern matching only. See tracex-nlp/README.txt to start it.";
    }

    /** Model name reported by /health, or empty when the service cannot be reached. */
    public Optional<String> health() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode model = mapper.readTree(response.body()).get("model");
            return Optional.of(model == null ? "unknown" : model.asText());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** Sends the texts to the service in chunks. Empty when disabled or when anything goes wrong. */
    public Optional<NlpResult> extract(List<String> texts) {
        if (!enabled || texts.isEmpty()) {
            return Optional.empty();
        }
        List<EntityExtractor.Extracted> all = new ArrayList<>();
        String model = "unknown";
        try {
            for (int from = 0; from < texts.size(); from += CHUNK) {
                List<String> part = texts.subList(from, Math.min(texts.size(), from + CHUNK));
                ObjectNode body = mapper.createObjectNode();
                ArrayNode array = body.putArray("texts");
                for (String text : part) {
                    array.add(text == null ? "" : text);
                }
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/extract"))
                        .timeout(Duration.ofSeconds(180))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    LOG.warn("AI service answered HTTP {}; using pattern matching instead.", response.statusCode());
                    return Optional.empty();
                }
                JsonNode root = mapper.readTree(response.body());
                JsonNode results = root.get("results");
                if (results == null || !results.isArray() || results.size() != part.size()) {
                    LOG.warn("AI service returned an unexpected answer; using pattern matching instead.");
                    return Optional.empty();
                }
                if (root.hasNonNull("model")) {
                    model = root.get("model").asText();
                }
                for (JsonNode item : results) {
                    all.add(toExtracted(item));
                }
            }
            return Optional.of(new NlpResult(model, all));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException ex) {
            LOG.warn("AI service could not be used ({}); using pattern matching instead.", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static EntityExtractor.Extracted toExtracted(JsonNode item) {
        EntityExtractor.Extracted found = new EntityExtractor.Extracted();
        read(item.get("persons"), found.persons);
        read(item.get("places"), found.places);
        read(item.get("phones"), found.phones);
        read(item.get("plates"), found.plates);
        read(item.get("accounts"), found.accounts);
        return found;
    }

    private static void read(JsonNode array, Set<String> target) {
        if (array == null || !array.isArray()) {
            return;
        }
        for (JsonNode value : array) {
            if (value.isTextual() && !value.asText().isBlank()) {
                target.add(value.asText().trim());
            }
        }
    }
}
