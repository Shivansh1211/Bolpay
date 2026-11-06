package com.bolpay.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MlScoringClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofMillis(500);
    private final ObjectMapper objectMapper;

    @Value("${ml-service.url:http://ml-service:5000}")
    private String serviceUrl;

    @Value("${ml-service.api-key:}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    public MlScoreResponse score(Map<String, Double> numericFeatures) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ML API key is not configured");
        }
        String base = serviceUrl.endsWith("/") ? serviceUrl.substring(0, serviceUrl.length() - 1) : serviceUrl;
        String body = objectMapper.writeValueAsString(Map.of("features", numericFeatures));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/score"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("ML scoring endpoint returned HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), MlScoreResponse.class);
    }

    public record MlScoreResponse(
            @JsonProperty("anomaly_score") double score,
            @JsonProperty("is_suspicious") boolean suspicious,
            List<String> reasons,
            @JsonProperty("model_version") String modelVersion,
            String scorer) {}
}
