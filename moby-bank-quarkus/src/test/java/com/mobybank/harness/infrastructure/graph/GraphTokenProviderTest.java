package com.mobybank.harness.infrastructure.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.domain.DocumentSourceException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphTokenProviderTest {

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-30T12:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private FakeGraphServer server;
    private final HttpClient http = HttpClient.newHttpClient();
    private final MutableClock clock = new MutableClock();

    @BeforeEach
    void setUp() {
        server = FakeGraphServer.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private GraphSettings settings(Optional<String> token, Optional<String> clientId, Optional<String> secret) {
        return new GraphSettings(server.baseUrl(), "me/drive", Optional.empty(), token, server.tokenUrl(), clientId,
                secret, Duration.ofSeconds(5), 1024);
    }

    @Test
    void aConfiguredTokenIsUsedAsIsWithoutCallingAnyEndpoint() {
        GraphTokenProvider tokens = new GraphTokenProvider(settings(Optional.of("  static-token "), Optional.empty(),
                Optional.empty()), http, clock);
        assertEquals("static-token", tokens.token());
        assertTrue(server.hits.isEmpty());
    }

    @Test
    void clientCredentialsPostAFormAndTheTokenIsReusedUntilShortlyBeforeItExpires() {
        GraphTokenProvider tokens = new GraphTokenProvider(
                settings(Optional.empty(), Optional.of("client id"), Optional.of("s3cr&t=")), http, clock);

        assertEquals("good-token", tokens.token());
        assertEquals("good-token", tokens.token());
        assertEquals(1, server.tokenRequestBodies.size(), "the second call reuses the cached token");

        String form = server.tokenRequestBodies.get(0);
        assertTrue(form.contains("client_id=client+id"), form);
        assertTrue(form.contains("client_secret=s3cr%26t%3D"), "the secret is form-encoded: " + form);
        assertTrue(form.contains("scope=https%3A%2F%2Fgraph.microsoft.com%2F.default"), form);
        assertTrue(form.contains("grant_type=client_credentials"), form);

        clock.advance(Duration.ofSeconds(3600 - 61));
        tokens.token();
        assertEquals(1, server.tokenRequestBodies.size(), "still valid 61 seconds before expiry");

        clock.advance(Duration.ofSeconds(2));
        tokens.token();
        assertEquals(2, server.tokenRequestBodies.size(), "refreshed inside the last minute");
    }

    @Test
    void theClientSecretNeverAppearsInAnErrorMessage() {
        server.override(uri -> uri.getPath().equals("/token"), exchange -> FakeGraphServer.json(exchange, 401,
                "{\"error\":\"invalid_client\",\"error_description\":\"AADSTS7000215: Invalid client secret provided.\\r\\nTrace ID: abc\\r\\nTimestamp: now\"}"));
        GraphTokenProvider tokens = new GraphTokenProvider(
                settings(Optional.empty(), Optional.of("cid"), Optional.of("very-secret-value")), http, clock);

        DocumentSourceException e = assertThrows(DocumentSourceException.class, tokens::token);

        assertEquals("Could not get a Microsoft Graph token: AADSTS7000215: Invalid client secret provided.", e.getMessage());
        assertFalse(e.getMessage().contains("very-secret-value"));
    }

    @Test
    void aResponseWithoutATokenIsAnError() {
        server.override(uri -> uri.getPath().equals("/token"), exchange -> FakeGraphServer.json(exchange, 200, "{}"));
        GraphTokenProvider tokens = new GraphTokenProvider(
                settings(Optional.empty(), Optional.of("cid"), Optional.of("s")), http, clock);
        assertThrows(DocumentSourceException.class, tokens::token);
    }

    @Test
    void anUnreachableTokenEndpointIsReported() {
        GraphSettings unreachable = new GraphSettings(server.baseUrl(), "me/drive", Optional.empty(), Optional.empty(),
                "http://127.0.0.1:1/token", Optional.of("cid"), Optional.of("s"), Duration.ofSeconds(2), 1024);
        DocumentSourceException e = assertThrows(DocumentSourceException.class,
                () -> new GraphTokenProvider(unreachable, http, clock).token());
        assertTrue(e.getMessage().contains("Could not reach the Microsoft token endpoint"), e.getMessage());
    }

    @Test
    void configurationWithNeitherATokenNorClientCredentialsFailsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new GraphTokenProvider(
                settings(Optional.empty(), Optional.empty(), Optional.empty()), http, clock));
        assertTrue(e.getMessage().contains("harness.graph.access-token"), e.getMessage());

        assertThrows(IllegalStateException.class, () -> new GraphTokenProvider(
                settings(Optional.of("  "), Optional.of("cid"), Optional.empty()), http, clock), "a blank token and half the credentials");
    }
}
