package com.mobybank.harness.interfaces.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mobybank.harness.application.MicrosoftTokens;
import com.mobybank.harness.application.UserGraphTokens;
import com.mobybank.harness.domain.UserId;
import com.mobybank.harness.infrastructure.graph.FakeEntraServer;
import com.mobybank.harness.infrastructure.graph.FakeGraphServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * "Sign in with Microsoft" end to end against a fake Entra and a fake Graph: the redirect to Microsoft with PKCE, the
 * code coming back, the account created, the session cookie, and OneDrive then read with that user's own token.
 */
@QuarkusTest
@QuarkusTestResource(value = MicrosoftFakes.class, restrictToAnnotatedClass = true)
class MicrosoftSignInTest {

    @Inject
    UserGraphTokens graphTokens;

    private static FakeEntraServer entra() {
        return MicrosoftFakes.entra;
    }

    private static FakeGraphServer graph() {
        return MicrosoftFakes.graph;
    }

    /** Walks the whole redirect dance by hand, like a browser, and returns the app's session cookie. */
    private static String signInWithMicrosoft() {
        Map<String, String> cookies = new HashMap<>();
        Response start = given().redirects().follow(false).get("/auth/microsoft");
        assertEquals(302, start.statusCode(), "an anonymous visitor is sent to Microsoft: " + start.asString());
        cookies.putAll(start.cookies());
        String authorize = start.header("Location");
        assertTrue(authorize.startsWith(entra().origin()), authorize);

        Response atMicrosoft = given().urlEncodingEnabled(false).redirects().follow(false).get(URI.create(authorize));
        assertEquals(302, atMicrosoft.statusCode());
        String callback = atMicrosoft.header("Location");
        assertTrue(callback.contains("/auth/microsoft/callback?code="), callback);

        Response back = given().urlEncodingEnabled(false).redirects().follow(false).cookies(cookies).get(URI.create(callback));
        assertEquals(302, back.statusCode(), "the callback hands over to the protected path: " + back.asString());
        cookies.putAll(back.cookies());

        Response done = given().redirects().follow(false).cookies(cookies).get(URI.create(back.header("Location")));
        assertEquals(303, done.statusCode(), "signed in: " + done.asString());
        String session = done.cookie(AuthFilter.COOKIE);
        assertNotNull(session, "the app's own session cookie is set");
        return session;
    }

    private static String idOf(String session) {
        return given().cookie(AuthFilter.COOKIE, session).get("/api/me").then().statusCode(200).extract().path("id");
    }

    @Test
    void thePageOffersMicrosoftWhenItIsSetUp() {
        given().get("/auth/options").then().statusCode(200).body("microsoft", equalTo(true));
    }

    @Test
    void theVisitorIsSentToMicrosoftWithPkceAndTheGraphScopes() {
        Response start = given().redirects().follow(false).get("/auth/microsoft");
        given().urlEncodingEnabled(false).redirects().follow(false).get(URI.create(start.header("Location")));

        Map<String, String> query = entra().authorizeRequests.get(entra().authorizeRequests.size() - 1).query();
        assertEquals(FakeEntraServer.CLIENT_ID, query.get("client_id"));
        assertEquals("code", query.get("response_type"));
        assertEquals("S256", query.get("code_challenge_method"));
        assertNotNull(query.get("code_challenge"));
        assertNotNull(query.get("state"));
        assertTrue(query.get("redirect_uri").endsWith("/auth/microsoft/callback"), query.get("redirect_uri"));
        for (String scope : new String[] {"openid", "offline_access", "Files.Read"}) {
            assertTrue(query.get("scope").contains(scope), query.get("scope"));
        }
    }

    @Test
    void signingInCreatesTheAccountFromTheIdTokenAndStartsASession() {
        entra().oid = "oid-new-user";
        entra().name = "Ada Lovelace";
        entra().email = "Ada.Lovelace@Example.com";

        String session = signInWithMicrosoft();

        given().cookie(AuthFilter.COOKIE, session).get("/api/me").then().statusCode(200)
                .body("name", equalTo("Ada Lovelace")).body("initials", equalTo("AL"));
        given().cookie(AuthFilter.COOKIE, session).get("/").then().statusCode(200);
        given().cookie(AuthFilter.COOKIE, session).get("/api/sessions").then().statusCode(200);
    }

    @Test
    void theNextSignInIsTheSameAccount() {
        entra().oid = "oid-returning";
        entra().name = "Grace Hopper";
        entra().email = "grace@example.com";

        String first = idOf(signInWithMicrosoft());
        String second = idOf(signInWithMicrosoft());

        assertEquals(first, second);
    }

    @Test
    void theGraphTokensStayOnTheServerAndOneDriveIsReadWithTheUsersOwnToken() {
        entra().oid = "oid-onedrive";
        entra().name = "Katherine Johnson";
        entra().email = "katherine@example.com";
        entra().accessToken = FakeGraphServer.GOOD_TOKEN;
        String session = signInWithMicrosoft();
        UserId user = new UserId(idOf(session));

        MicrosoftTokens tokens = graphTokens.find(user).orElseThrow();
        assertEquals(FakeGraphServer.GOOD_TOKEN, tokens.accessToken());
        assertEquals("refresh-1", tokens.refreshToken());

        graph().hits.clear();
        given().cookie(AuthFilter.COOKIE, session).get("/api/folders").then().statusCode(200)
                .body("name", hasItem("Earnings 2026"));
        assertFalse(graph().hits.isEmpty());
        assertTrue(graph().hits.stream().filter(h -> h.path().startsWith("/v1.0/"))
                .allMatch(h -> ("Bearer " + FakeGraphServer.GOOD_TOKEN).equals(h.authorization())),
                "every Graph request carried the user's token");
        given().cookie(AuthFilter.COOKIE, session).get("/ui/user").then().body(not(containsString(tokens.refreshToken())));
    }

    @Test
    void anExpiredAccessTokenIsRenewedWithTheRefreshTokenAndTheUserNeverNotices() {
        entra().oid = "oid-expiring";
        entra().name = "Mary Jackson";
        entra().email = "mary@example.com";
        String session = signInWithMicrosoft();
        UserId user = new UserId(idOf(session));
        graphTokens.remember(user, new MicrosoftTokens("stale", "refresh-1", Instant.now().minusSeconds(30)));
        int before = entra().tokenRequests.size();

        given().cookie(AuthFilter.COOKIE, session).get("/api/folders").then().statusCode(200);

        assertEquals(before + 1, entra().tokenRequests.size());
        Map<String, String> renewal = entra().tokenRequests.get(before);
        assertEquals("refresh_token", renewal.get("grant_type"));
        assertEquals("refresh-1", renewal.get("refresh_token"));
        assertEquals(FakeGraphServer.GOOD_TOKEN, graphTokens.find(user).orElseThrow().accessToken());
    }

    @Test
    void whenMicrosoftRefusesTheRefreshTokenOneDriveAsksForANewSignIn() {
        entra().oid = "oid-revoked";
        entra().name = "Dorothy Vaughan";
        entra().email = "dorothy@example.com";
        String session = signInWithMicrosoft();
        UserId user = new UserId(idOf(session));
        graphTokens.remember(user, new MicrosoftTokens("stale", "revoked-by-admin", Instant.now().minusSeconds(30)));

        given().cookie(AuthFilter.COOKIE, session).get("/api/folders").then().statusCode(401)
                .body("error", equalTo("sign_in_required")).body("message", containsString("Sign in with Microsoft"));
        assertTrue(graphTokens.find(user).isEmpty());
        given().cookie(AuthFilter.COOKIE, session).get("/api/me").then().statusCode(200); // still signed in to the app
    }

    @Test
    void anAnalystWhoSignedInWithAPasswordMustSignInWithMicrosoftToUseOneDrive() {
        Response login = given().redirects().follow(false).contentType(ContentType.URLENC)
                .formParam("email", "hermione.granger@mobybank.com").formParam("password", "moby-demo")
                .post("/auth/login");
        String session = login.cookie(AuthFilter.COOKIE);

        given().cookie(AuthFilter.COOKIE, session).get("/api/folders").then().statusCode(401)
                .body("error", equalTo("sign_in_required"));
        given().cookie(AuthFilter.COOKIE, session).get("/api/sessions").then().statusCode(200);
    }

    @Test
    void signingOutForgetsTheMicrosoftTokens() {
        entra().oid = "oid-signing-out";
        entra().name = "Annie Easley";
        entra().email = "annie@example.com";
        String session = signInWithMicrosoft();
        UserId user = new UserId(idOf(session));
        assertTrue(graphTokens.find(user).isPresent());

        given().redirects().follow(false).cookie(AuthFilter.COOKIE, session).post("/auth/logout").then().statusCode(303);

        assertTrue(graphTokens.find(user).isEmpty());
        given().cookie(AuthFilter.COOKIE, session).get("/api/folders").then().statusCode(401);
    }

    @Test
    void aSignInThatMicrosoftRefusesGoesBackToTheSignInPageWithAMessage() {
        entra().denyNext = true;
        Map<String, String> cookies = new HashMap<>();
        Response start = given().redirects().follow(false).get("/auth/microsoft");
        cookies.putAll(start.cookies());
        Response atMicrosoft = given().urlEncodingEnabled(false).redirects().follow(false)
                .get(URI.create(start.header("Location")));
        String callback = atMicrosoft.header("Location");
        assertTrue(callback.contains("error=access_denied"), callback);

        Response back = given().urlEncodingEnabled(false).redirects().follow(false).cookies(cookies)
                .get(URI.create(callback));

        assertEquals(302, back.statusCode(), back.asString());
        assertTrue(back.header("Location").contains("/auth/error"), back.header("Location"));
        cookies.putAll(back.cookies());
        cookies.values().removeIf(String::isBlank); // a browser drops the cookies the server expired
        Response error = given().urlEncodingEnabled(false).redirects().follow(false).cookies(cookies)
                .get(URI.create(back.header("Location")));
        error.then().statusCode(303).header("Location", containsString("/login.html?error=microsoft"));
        assertEquals(null, error.cookie(AuthFilter.COOKIE));
    }

    @Test
    void aCallbackWithAMadeUpStateDoesNotSignAnyoneIn() {
        Response start = given().redirects().follow(false).get("/auth/microsoft");
        Map<String, String> cookies = new HashMap<>(start.cookies());

        Response forged = given().redirects().follow(false).cookies(cookies)
                .get("/auth/microsoft/callback?code=code-x&state=not-the-state");

        assertEquals(null, forged.cookie(AuthFilter.COOKIE));
        given().cookies(cookies).get("/api/me").then().statusCode(401);
    }
}
