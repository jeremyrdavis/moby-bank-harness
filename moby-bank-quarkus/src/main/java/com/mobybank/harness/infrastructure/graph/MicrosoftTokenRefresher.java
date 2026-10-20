package com.mobybank.harness.infrastructure.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobybank.harness.application.MicrosoftTokens;
import com.mobybank.harness.domain.DocumentSourceException;
import com.mobybank.harness.domain.SignInRequiredException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/**
 * Trades a user's refresh token for a new access token at Microsoft's token endpoint (the refresh-token grant, with the
 * app registration's client id and secret). Microsoft may hand out a new refresh token too; the caller keeps whichever
 * comes back. A refusal ({@code invalid_grant}: the user revoked access or the token expired) means the user has to
 * sign in with Microsoft again.
 */
public final class MicrosoftTokenRefresher {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient http;
    private final Duration timeout;
    private final Clock clock;

    public MicrosoftTokenRefresher(String tokenUrl, String clientId, String clientSecret, HttpClient http,
                                   Duration timeout, Clock clock) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.http = http;
        this.timeout = timeout;
        this.clock = clock;
    }

    /**
     * @throws SignInRequiredException if Microsoft refuses the refresh token
     * @throws DocumentSourceException if Microsoft cannot be reached or answers with something unusable
     */
    public MicrosoftTokens refresh(MicrosoftTokens old) {
        if (old.refreshToken() == null || old.refreshToken().isBlank()) {
            throw new SignInRequiredException("Your Microsoft sign-in has expired. Sign in with Microsoft again.");
        }
        String form = "grant_type=refresh_token&client_id=" + encode(clientId)
                + (clientSecret == null ? "" : "&client_secret=" + encode(clientSecret))
                + "&refresh_token=" + encode(old.refreshToken());
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl)).timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = JSON.readTree(response.body().isBlank() ? "{}" : response.body());
            if (response.statusCode() / 100 != 2) {
                String error = body.path("error").asText("");
                if (response.statusCode() == 400 || response.statusCode() == 401 || "invalid_grant".equals(error)) {
                    throw new SignInRequiredException(
                            "Your Microsoft sign-in has expired. Sign in with Microsoft again.");
                }
                throw new DocumentSourceException("Microsoft would not renew the sign-in (HTTP "
                        + response.statusCode() + ")");
            }
            String access = body.path("access_token").asText("");
            if (access.isBlank()) {
                throw new DocumentSourceException("Microsoft answered without an access token");
            }
            String refresh = body.path("refresh_token").asText("");
            return new MicrosoftTokens(access, refresh.isBlank() ? old.refreshToken() : refresh,
                    clock.instant().plusSeconds(body.path("expires_in").asLong(3600)));
        } catch (IOException e) {
            throw new DocumentSourceException("Could not reach the Microsoft token endpoint: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentSourceException("Interrupted while renewing the Microsoft sign-in", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
