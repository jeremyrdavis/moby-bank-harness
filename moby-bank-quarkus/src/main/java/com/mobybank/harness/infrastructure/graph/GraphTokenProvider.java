package com.mobybank.harness.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobybank.harness.domain.DocumentSourceException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Supplies the bearer token for Microsoft Graph: either a configured token used as-is, or one obtained with the
 * OAuth client-credentials flow and reused until a minute before it expires.
 */
public final class GraphTokenProvider {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration EARLY_REFRESH = Duration.ofSeconds(60);

    private final GraphSettings settings;
    private final HttpClient http;
    private final Clock clock;

    private String cachedToken;
    private Instant cachedUntil = Instant.MIN;

    public GraphTokenProvider(GraphSettings settings, HttpClient http, Clock clock) {
        this.settings = settings;
        this.http = http;
        this.clock = clock;
        if (settings.accessToken().filter(token -> !token.isBlank()).isEmpty()
                && (settings.clientId().isEmpty() || settings.clientSecret().isEmpty())) {
            throw new IllegalStateException("harness.documents.mode=graph needs either harness.graph.access-token "
                    + "or harness.graph.client-id with harness.graph.client-secret");
        }
    }

    public synchronized String token() {
        if (settings.accessToken().filter(token -> !token.isBlank()).isPresent()) {
            return settings.accessToken().get().strip();
        }
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(cachedUntil.minus(EARLY_REFRESH))) {
            return cachedToken;
        }
        JsonNode response = requestToken();
        String token = response.path("access_token").asText("");
        if (token.isBlank()) {
            throw new DocumentSourceException("The token endpoint answered without an access token");
        }
        cachedToken = token;
        cachedUntil = now.plusSeconds(response.path("expires_in").asLong(3600));
        return token;
    }

    private JsonNode requestToken() {
        String form = "client_id=" + encode(settings.clientId().orElseThrow())
                + "&client_secret=" + encode(settings.clientSecret().orElseThrow())
                + "&scope=" + encode("https://graph.microsoft.com/.default")
                + "&grant_type=client_credentials";
        HttpRequest request = HttpRequest.newBuilder(URI.create(settings.tokenUrl()))
                .timeout(settings.requestTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = JSON.readTree(response.body().isBlank() ? "{}" : response.body());
            if (response.statusCode() / 100 != 2) {
                String why = body.path("error_description").asText(body.path("error").asText("HTTP " + response.statusCode()));
                throw new DocumentSourceException("Could not get a Microsoft Graph token: " + firstLine(why));
            }
            return body;
        } catch (IOException e) {
            throw new DocumentSourceException("Could not reach the Microsoft token endpoint: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentSourceException("Interrupted while getting a Microsoft Graph token", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse(text);
    }
}
