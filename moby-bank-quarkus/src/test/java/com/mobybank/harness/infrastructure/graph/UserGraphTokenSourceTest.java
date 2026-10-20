package com.mobybank.harness.infrastructure.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.MicrosoftTokens;
import com.mobybank.harness.domain.DocumentSourceException;
import com.mobybank.harness.domain.SignInRequiredException;
import com.mobybank.harness.domain.UserId;
import com.mobybank.harness.infrastructure.InMemoryUserGraphTokens;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Each user reads OneDrive with their own Microsoft token, renewed when it is about to expire. */
class UserGraphTokenSourceTest {

    private static final UserId ADA = new UserId("user-ada");
    private static final UserId GRACE = new UserId("user-grace");

    private FakeGraphServer server;
    private final HttpClient http = HttpClient.newHttpClient();
    private final InMemoryUserGraphTokens store = new InMemoryUserGraphTokens();
    private final Clock clock = Clock.systemUTC();
    private GraphSettings settings;

    @BeforeEach
    void setUp() {
        server = FakeGraphServer.start();
        settings = new GraphSettings(server.baseUrl(), "me/drive", Optional.empty(), Optional.empty(), server.tokenUrl(),
                Optional.empty(), Optional.empty(), Duration.ofSeconds(5), 1024 * 1024);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private MicrosoftTokenRefresher refresher() {
        return new MicrosoftTokenRefresher(server.tokenUrl(), "client-1", "s3cret & more", http, Duration.ofSeconds(5),
                clock);
    }

    private GraphDocumentCatalog catalog(GraphTokenProvider appWide) {
        return new GraphDocumentCatalog(settings, new UserGraphTokenSource(store, appWide, refresher(), clock), http);
    }

    private GraphTokenProvider appWideCredential() {
        GraphSettings withToken = new GraphSettings(settings.baseUrl(), settings.drive(), Optional.empty(),
                Optional.of("app-wide-token"), settings.tokenUrl(), Optional.empty(), Optional.empty(),
                Duration.ofSeconds(5), 1024);
        return new GraphTokenProvider(withToken, http, clock);
    }

    private static MicrosoftTokens valid(String access) {
        return new MicrosoftTokens(access, "refresh-of-" + access, Instant.now().plusSeconds(3600));
    }

    private List<String> authorizationsOnTheDrive() {
        return server.hitsOn("/v1.0/").stream().map(FakeGraphServer.Hit::authorization).toList();
    }

    @Test
    void everyUserIsServedWithTheirOwnToken() {
        store.remember(ADA, valid(FakeGraphServer.GOOD_TOKEN));
        store.remember(GRACE, valid("graces-token"));
        GraphDocumentCatalog catalog = catalog(null);

        assertFalse(catalog.folders(ADA).isEmpty());
        assertThrows(DocumentSourceException.class, () -> catalog.folders(GRACE)); // the fake only accepts Ada's token

        assertEquals(java.util.Set.of("Bearer " + FakeGraphServer.GOOD_TOKEN, "Bearer graces-token"),
                new java.util.LinkedHashSet<>(authorizationsOnTheDrive()));
        assertTrue(authorizationsOnTheDrive().indexOf("Bearer graces-token") > authorizationsOnTheDrive()
                .lastIndexOf("Bearer " + FakeGraphServer.GOOD_TOKEN), "Grace's request came after Ada's");
    }

    @Test
    void aTokenThatIsAboutToExpireIsRenewedWithTheRefreshTokenAndKept() {
        store.remember(ADA, new MicrosoftTokens("stale-token", "ada-refresh", Instant.now().plusSeconds(10)));

        List<?> folders = catalog(null).folders(ADA);

        assertFalse(folders.isEmpty(), "the renewed token (the fake's good token) was used");
        assertEquals(1, server.tokenRequestBodies.size());
        String body = server.tokenRequestBodies.get(0);
        assertTrue(body.contains("grant_type=refresh_token"), body);
        assertTrue(body.contains("refresh_token=ada-refresh"), body);
        assertTrue(body.contains("client_id=client-1"), body);
        assertTrue(body.contains("client_secret=s3cret+%26+more"), "the secret is form-encoded: " + body);
        MicrosoftTokens kept = store.find(ADA).orElseThrow();
        assertEquals(FakeGraphServer.GOOD_TOKEN, kept.accessToken());
        assertEquals("ada-refresh", kept.refreshToken(), "no new refresh token came back, so the old one stays");
        assertTrue(kept.expiresAt().isAfter(Instant.now().plusSeconds(3000)));
    }

    @Test
    void aTokenThatIsStillGoodIsNotRenewed() {
        store.remember(ADA, valid(FakeGraphServer.GOOD_TOKEN));

        catalog(null).folders(ADA);
        catalog(null).folders(ADA);

        assertTrue(server.tokenRequestBodies.isEmpty());
    }

    @Test
    void aNewRefreshTokenFromMicrosoftReplacesTheOldOne() {
        server.override(uri -> uri.getPath().equals("/token"), exchange -> FakeGraphServer.json(exchange, 200,
                "{\"access_token\":\"" + FakeGraphServer.GOOD_TOKEN + "\",\"refresh_token\":\"rotated\",\"expires_in\":600}"));
        store.remember(ADA, new MicrosoftTokens("stale", "old-refresh", Instant.now().minusSeconds(5)));

        catalog(null).folders(ADA);

        assertEquals("rotated", store.find(ADA).orElseThrow().refreshToken());
    }

    @Test
    void whenMicrosoftRefusesTheRefreshTokenTheUserMustSignInAgainAndTheTokensAreForgotten() {
        server.override(uri -> uri.getPath().equals("/token"), exchange -> FakeGraphServer.json(exchange, 400,
                "{\"error\":\"invalid_grant\",\"error_description\":\"AADSTS70008: expired\"}"));
        store.remember(ADA, new MicrosoftTokens("stale", "revoked", Instant.now().minusSeconds(5)));

        SignInRequiredException e = assertThrows(SignInRequiredException.class, () -> catalog(null).folders(ADA));

        assertTrue(e.getMessage().contains("Sign in with Microsoft"), e.getMessage());
        assertTrue(store.find(ADA).isEmpty());
        assertFalse(e.getMessage().contains("revoked"), "the refresh token is not in the message");
    }

    @Test
    void anExpiredTokenWithNoRefreshTokenNeedsASignIn() {
        store.remember(ADA, new MicrosoftTokens("stale", null, Instant.now().minusSeconds(5)));

        assertThrows(SignInRequiredException.class, () -> catalog(null).folders(ADA));
        assertTrue(server.tokenRequestBodies.isEmpty(), "nothing to send");
    }

    @Test
    void microsoftBeingUnreachableIsADocumentSourceFailureNotASignOut() {
        server.override(uri -> uri.getPath().equals("/token"),
                exchange -> FakeGraphServer.json(exchange, 503, "{\"error\":\"temporarily_unavailable\"}"));
        store.remember(ADA, new MicrosoftTokens("stale", "ada-refresh", Instant.now().minusSeconds(5)));

        assertThrows(DocumentSourceException.class, () -> catalog(null).folders(ADA));
        assertTrue(store.find(ADA).isPresent(), "a hiccup does not sign the user out");
    }

    @Test
    void aUserWhoNeverSignedInWithMicrosoftNeedsToWhenThereIsNoAppWideCredential() {
        SignInRequiredException e = assertThrows(SignInRequiredException.class, () -> catalog(null).folders(ADA));

        assertEquals("Sign in with Microsoft to use your OneDrive.", e.getMessage());
        assertTrue(server.hits.isEmpty(), "Graph was not called without a token");
    }

    @Test
    void aUserWhoNeverSignedInWithMicrosoftFallsBackToTheAppWideCredential() {
        GraphDocumentCatalog catalog = catalog(appWideCredential());

        assertThrows(DocumentSourceException.class, () -> catalog.folders(ADA)); // the fake rejects that token

        assertEquals(List.of("Bearer app-wide-token"), authorizationsOnTheDrive());
    }

    @Test
    void theRefreshTokenGoesOnlyToTheTokenEndpointNotToGraph() {
        store.remember(ADA, new MicrosoftTokens("stale", "super-secret-refresh", Instant.now().minusSeconds(5)));

        catalog(null).folders(ADA);

        assertTrue(server.hits.stream().filter(h -> h.path().startsWith("/v1.0/"))
                .noneMatch(h -> String.valueOf(h.authorization()).contains("super-secret-refresh")));
        assertTrue(server.tokenRequestBodies.get(0).contains("super-secret-refresh"));
    }
}
